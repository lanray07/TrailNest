package com.trailnest.app.billing

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class BillingConfig(val enabled: Boolean = false, val parentSku: String = "", val annualSku: String = "")
data class BillingState(val configured: Boolean = false, val price: String? = null, val busy: Boolean = false,
    val pending: Boolean = false, val problem: Boolean = false, val premiumUntil: Long? = null, val canBuy: Boolean = false) {
    val premium: Boolean get() = premiumUntil != null
}
data class StoreOffer(val sku: String, val price: String, val type: String, val period: String?, val promotional: Boolean = false)
// App Tester echoes the console export's "Annually" spelling; production uses "Annual".
internal fun amazonSubscriptionPeriod(period: String?, sandbox: Boolean): String? =
    if (sandbox && period == "Annually") "Annual" else period
data class StoreReceipt(val id: String, val sku: String, val type: String)
sealed interface StoreEvent {
    data class User(val request: String, val user: String?) : StoreEvent
    data class Products(val request: String, val offer: StoreOffer?) : StoreEvent
    data class Updates(val request: String, val user: String?, val receipts: List<StoreReceipt>, val more: Boolean, val success: Boolean) : StoreEvent
    data class Purchase(val request: String, val user: String?, val receipt: StoreReceipt?, val status: String) : StoreEvent
}
interface AmazonStore {
    fun register(listener: (StoreEvent) -> Unit)
    fun user(): String
    fun product(sku: String): String
    fun updates(reset: Boolean): String
    fun purchase(sku: String): String
    fun fulfilled(receipt: String)
}
sealed interface ReceiptResult {
    data class Signed(val token: String) : ReceiptResult
    data object Denied : ReceiptResult
    data object Retry : ReceiptResult
}
fun interface ReceiptVerifier {
    suspend fun verify(user: String, receipt: String): ReceiptResult
    suspend fun ready(): Boolean = false
}

/** Main-thread state machine; request IDs and generations reject stale/account-switched callbacks. */
class BillingController(private val config: BillingConfig, private val store: AmazonStore, private val verifier: ReceiptVerifier,
    private val guard: LeaseGuard?, private val cache: LeaseStore, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(BillingState(configured = config.enabled))
    val state: StateFlow<BillingState> = mutable
    private var started = false
    private var generation = 0L
    private var verification = 0L
    private var user: String? = null
    private var userRequest: String? = null
    private var productRequest: String? = null
    private var updateRequest: String? = null
    private var buyRequest: String? = null
    private var buyOwner: String? = null
    private var recentOwner: String? = null
    private val recentPurchases = linkedMapOf<String, StoreReceipt>()
    private val receipts = linkedMapOf<String, StoreReceipt>()
    private var pages = 0
    private var restoring = false
    private var checking = false
    private var restored = false
    private var cacheAllowed = true

    fun start() {
        if (!config.enabled || started) return
        require(guard != null && config.parentSku.isNotBlank() && config.annualSku.isNotBlank())
        started = true
        try { store.register(::event) } catch (_: Exception) { started = false; mutable.value = BillingState(problem = true) }
        if (started) scope.launch { while (isActive) { delay(30_000); tick() } }
    }
    fun refresh() {
        if (!started) return
        generation++; verification++; user = null; restored = false; checking = false
        receipts.clear(); pages = 0; userRequest = null; updateRequest = null; productRequest = null; restoring = true
        mutable.value = mutable.value.copy(price = null, premiumUntil = null, busy = true, problem = false, canBuy = false)
        try { userRequest = store.user(); timeout(userRequest) } catch (_: Exception) { unavailable() }
    }
    private fun timeout(request: String?) {
        val epoch = generation
        scope.launch { delay(30_000); if (epoch == generation && request != null && (request == userRequest || request == updateRequest)) { userRequest = null; updateRequest = null; unavailable() } }
    }
    fun buy() {
        val owner = user ?: return
        if (!state.value.canBuy) return
        val epoch = generation
        checking = true; tick()
        scope.launch {
            val ready = try { withTimeoutOrNull(10_000) { verifier.ready() } == true } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { false }
            if (epoch != generation || user != owner) return@launch
            checking = false
            if (!ready) { unavailable(); return@launch }
            beginCheckout(owner)
        }
    }
    private fun beginCheckout(owner: String) {
        updateRequest = null; restoring = false; verification++; checking = false
        mutable.value = mutable.value.copy(busy = true, canBuy = false, problem = false, pending = false)
        try { buyOwner = owner; buyRequest = store.purchase(config.annualSku) } catch (_: Exception) { buyRequest = null; unavailable() }
        val request = buyRequest
        scope.launch { delay(120_000); if (request != null && buyRequest == request) { buyRequest = null; buyOwner = null; refresh() } }
    }
    fun tick() {
        val lease = if (cacheAllowed) user?.let { guard?.cached(cache.read(), it) } else null
        val busy = restoring || checking || buyRequest != null
        mutable.value = mutable.value.copy(premiumUntil = lease?.expiresAt, busy = busy,
            canBuy = started && user != null && restored && !busy && !state.value.problem && !state.value.pending && state.value.price != null && lease == null)
    }
    private fun unavailable() { restoring = false; checking = false; mutable.value = mutable.value.copy(problem = true); tick() }
    private fun event(event: StoreEvent) {
        when (event) {
            is StoreEvent.User -> {
                if (event.request != userRequest) return
                userRequest = null
                if (event.user.isNullOrBlank()) { user = null; unavailable(); return }
                user = event.user
                if (recentOwner != user) recentPurchases.clear()
                recentOwner = user
                receipts.putAll(recentPurchases)
                if (guard?.cached(cache.read(), event.user) == null) { cacheAllowed = false; cache.clear() }
                tick()
                try {
                    productRequest = store.product(config.annualSku)
                    updateRequest = store.updates(true); timeout(updateRequest)
                } catch (_: Exception) { unavailable() }
            }
            is StoreEvent.Products -> {
                if (event.request != productRequest) return
                productRequest = null
                val offer = event.offer?.takeIf { it.sku == config.annualSku && it.type == "SUBSCRIPTION" && it.period == "Annual" && !it.promotional && it.price.isNotBlank() && it.price.length <= 200 }
                mutable.value = mutable.value.copy(price = offer?.price); tick()
            }
            is StoreEvent.Updates -> {
                if (event.request != updateRequest) return
                updateRequest = null
                if (!event.success) { unavailable(); return }
                if (event.user != user) {
                    user = null; generation++; verification++; restored = false; cacheAllowed = false; cache.clear()
                    recentPurchases.clear(); recentOwner = null; unavailable(); return
                }
                if (++pages > 100 || receipts.size + event.receipts.size > 1000) { unavailable(); return }
                event.receipts.forEach { receipts[it.id] = it }
                if (event.more) {
                    try { updateRequest = store.updates(false); timeout(updateRequest) } catch (_: Exception) { unavailable() }
                } else { restoring = false; restored = true; verify(receipts.values.toList()) }
            }
            is StoreEvent.Purchase -> {
                if (event.request != buyRequest) {
                    // A late successful checkout must be reconciled, never directly trusted.
                    if (event.status == "SUCCESSFUL") refresh()
                    return
                }
                val owner = buyOwner; buyRequest = null; buyOwner = null
                when (event.status) {
                    "SUCCESSFUL" -> {
                        if (event.user != owner || event.receipt == null) { unavailable(); return }
                        if (recentOwner != owner) recentPurchases.clear()
                        recentOwner = owner; recentPurchases[event.receipt.id] = event.receipt
                        if (user == owner) {
                            updateRequest = null; restoring = false; restored = true
                            verify(receipts.values.toList() + recentPurchases.values)
                        } else if (user != null) refresh()
                    }
                    "ALREADY_PURCHASED" -> refresh()
                    "PENDING" -> { mutable.value = mutable.value.copy(pending = true); tick() }
                    "INVALID_SKU", "NOT_SUPPORTED", "INACTIVE_BASE_SUBSCRIPTION" -> { mutable.value = mutable.value.copy(price = null); tick() }
                    else -> { tick() } // Amazon presents checkout failure/cancellation UI itself.
                }
            }
        }
    }
    private fun verify(batch: List<StoreReceipt>) {
        val owner = user ?: return
        val epoch = generation; val serial = ++verification
        val eligible = batch.filter { it.sku == config.parentSku && it.type == "SUBSCRIPTION" && it.id.isNotBlank() }.distinctBy { it.id }
        checking = true; tick()
        scope.launch {
            val granted = mutableListOf<Pair<StoreReceipt, Lease>>()
            val denied = mutableSetOf<String>(); var retry = false
            for (receipt in eligible) {
                val result = try { verifier.verify(owner, receipt.id) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { ReceiptResult.Retry }
                when (result) {
                    is ReceiptResult.Signed -> {
                        val lease = guard?.verify(result.token, owner, receipt.id)
                        if (lease == null) retry = true else granted += receipt to lease
                    }
                    ReceiptResult.Denied -> denied += billingHash(receipt.id)
                    ReceiptResult.Retry -> retry = true
                }
                if (epoch != generation || serial != verification || owner != user) return@launch
            }
            if (epoch != generation || serial != verification || owner != user) return@launch
            val best = granted.maxByOrNull { it.second.expiresAt }?.second
            val previous = guard?.cached(cache.read(), owner)
            if (best != null) {
                if (cache.write(guard!!.checkpoint(best)) && guard.cached(cache.read(), owner) != null) {
                    cacheAllowed = true
                    mutable.value = mutable.value.copy(pending = false, problem = false)
                    for ((receipt, lease) in granted) if (guard.verify(lease.token, owner, receipt.id) != null) runCatching { store.fulfilled(receipt.id) }
                } else { cacheAllowed = false; cache.clear(); mutable.value = mutable.value.copy(problem = true) }
            } else if (!retry || previous?.receiptHash in denied) { cacheAllowed = false; cache.clear() }
            checking = false
            if (retry) mutable.value = mutable.value.copy(problem = true)
            tick()
        }
    }
}
