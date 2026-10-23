package com.trailnest.app.billing

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.amazon.device.iap.PurchasingListener
import com.amazon.device.iap.PurchasingService
import com.amazon.device.iap.model.*
import com.trailnest.app.BuildConfig
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AmazonSdkStore(private val context: Context, private val annualSku: String) : AmazonStore, PurchasingListener {
    private var listener: (StoreEvent) -> Unit = {}
    override fun register(listener: (StoreEvent) -> Unit) {
        this.listener = listener
        PurchasingService.registerListener(context.applicationContext, this)
        PurchasingService.enablePendingPurchases()
    }
    override fun user() = PurchasingService.getUserData().toString()
    override fun product(sku: String) = PurchasingService.getProductData(setOf(sku)).toString()
    override fun updates(reset: Boolean) = PurchasingService.getPurchaseUpdates(reset).toString()
    override fun purchase(sku: String) = PurchasingService.purchase(sku).toString()
    override fun fulfilled(receipt: String) = PurchasingService.notifyFulfillment(receipt, FulfillmentResult.FULFILLED)
    override fun onUserDataResponse(response: UserDataResponse) {
        listener(StoreEvent.User(response.requestId.toString(), response.userData?.userId?.takeIf { response.requestStatus == UserDataResponse.RequestStatus.SUCCESSFUL }))
    }
    override fun onProductDataResponse(response: ProductDataResponse) {
        val product = if (response.requestStatus == ProductDataResponse.RequestStatus.SUCCESSFUL) response.productData?.get(annualSku) else null
        val offer = product?.let { StoreOffer(it.sku, it.price ?: "", it.productType.name, it.subscriptionPeriod,
            !it.promotions.isNullOrEmpty() || !it.freeTrialPeriod.isNullOrBlank() || !it.bases.isNullOrEmpty()) }
        listener(StoreEvent.Products(response.requestId.toString(), offer))
    }
    private fun Receipt.model() = StoreReceipt(receiptId, sku, productType.name)
    override fun onPurchaseResponse(response: PurchaseResponse) {
        listener(StoreEvent.Purchase(response.requestId.toString(), response.userData?.userId, response.receipt?.model(), response.requestStatus.name))
    }
    override fun onPurchaseUpdatesResponse(response: PurchaseUpdatesResponse) {
        listener(StoreEvent.Updates(response.requestId.toString(), response.userData?.userId, response.receipts.orEmpty().map { it.model() }, response.hasMore(), response.requestStatus == PurchaseUpdatesResponse.RequestStatus.SUCCESSFUL))
    }
}

class HttpsReceiptVerifier(private val endpoint: String) : ReceiptVerifier {
    init {
        val url = URL(endpoint)
        require(url.protocol == "https" && url.userInfo == null && url.query == null && url.ref == null)
        require(url.path.endsWith("/v1/amazon/verify"))
    }
    override suspend fun ready(): Boolean = withContext(Dispatchers.IO) {
        val connection = URL(endpoint.removeSuffix("verify") + "ready").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false; connection.connectTimeout = 5000; connection.readTimeout = 5000
            connection.requestMethod = "HEAD"
            connection.responseCode == 204
        } catch (_: Exception) { false } finally { connection.disconnect() }
    }
    override suspend fun verify(user: String, receipt: String): ReceiptResult = withContext(Dispatchers.IO) {
        if (user.length !in 1..2048 || receipt.length !in 1..2048) return@withContext ReceiptResult.Denied
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false; connection.connectTimeout = 10_000; connection.readTimeout = 10_000
            connection.requestMethod = "POST"; connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            val body = JSONObject().put("amazonUserId", user).put("receiptId", receipt).toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            when (connection.responseCode) {
                200 -> {
                    val bytes = connection.inputStream.use { input ->
                        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(2048)
                        while (out.size() <= 16384) { val count = input.read(buffer, 0, minOf(buffer.size, 16385 - out.size())); if (count < 0) break; out.write(buffer, 0, count) }
                        out.toByteArray()
                    }
                    if (bytes.size > 16384) ReceiptResult.Retry else ReceiptResult.Signed(JSONObject(String(bytes, Charsets.UTF_8)).getString("lease"))
                }
                400, 403 -> ReceiptResult.Denied
                else -> ReceiptResult.Retry
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { ReceiptResult.Retry }
        finally { connection.disconnect() }
    }
}

class PrivateLeaseStore(context: Context) : LeaseStore {
    private val preferences = context.applicationContext.getSharedPreferences("amazon_entitlement_v1", Context.MODE_PRIVATE)
    override fun read(): CachedLease? = runCatching {
        preferences.getString("token", null)?.let { CachedLease(it, preferences.getLong("wall", 0), preferences.getLong("elapsed", 0), preferences.getInt("boot", -1)) }
    }.getOrNull()
    override fun write(value: CachedLease): Boolean = runCatching { preferences.edit().clear().putString("token", value.token).putLong("wall", value.savedWall).putLong("elapsed", value.savedElapsed).putInt("boot", value.boot).commit() }.getOrDefault(false)
    override fun clear() { runCatching { preferences.edit().clear().commit() } }
}

/** Application-only context and scope survive activity recreation; disabled builds do not call Amazon. */
class AmazonBillingRuntime private constructor(context: Context) {
    private val app = context.applicationContext as android.app.Application
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val processBootFallback = -(java.security.SecureRandom().nextInt(Int.MAX_VALUE - 1) + 1)
    private val clock = object : BillingClock {
        override fun wall() = System.currentTimeMillis()
        override fun elapsed() = SystemClock.elapsedRealtime()
        override fun boot() = runCatching { Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1).takeIf { it >= 0 } ?: processBootFallback
    }
    private val guard = if (BuildConfig.BILLING_ENABLED && (BuildConfig.DEBUG || BuildConfig.BILLING_ENVIRONMENT == "production")) runCatching {
        app.assets.open("AppstoreAuthenticationKey.pem").use { require(it.read() >= 0) }
        LeaseGuard(app.assets.open("billing-verifier-public.pem").bufferedReader().use { it.readText() }, BuildConfig.APPLICATION_ID,
            BuildConfig.BILLING_ENVIRONMENT, BuildConfig.BILLING_PARENT_SKU, BuildConfig.BILLING_ANNUAL_SKU, clock)
    }.getOrNull() else null
    private val controller = BillingController(BillingConfig(guard != null, BuildConfig.BILLING_PARENT_SKU, BuildConfig.BILLING_ANNUAL_SKU),
        AmazonSdkStore(app, BuildConfig.BILLING_ANNUAL_SKU),
        if (guard != null) HttpsReceiptVerifier(BuildConfig.BILLING_VERIFICATION_URL) else ReceiptVerifier { _, _ -> ReceiptResult.Retry },
        guard, PrivateLeaseStore(app), scope)
    val state = controller.state
    fun start() = controller.start()
    fun refresh() = controller.refresh()
    fun buy() = controller.buy()
    fun hasPremium(): Boolean { controller.tick(); return state.value.premium }
    companion object {
        @Volatile private var instance: AmazonBillingRuntime? = null
        fun get(context: Context): AmazonBillingRuntime = instance ?: synchronized(this) { instance ?: AmazonBillingRuntime(context).also { instance = it } }
    }
}
