package com.trailnest.app.billing

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class BillingTest {
    @Test fun appTesterAnnualSpellingIsNormalizedOnlyInSandbox() {
        assertEquals("Annual", amazonSubscriptionPeriod("Annually", true))
        assertEquals("Annual", amazonSubscriptionPeriod("Annual", true))
        assertEquals("Annually", amazonSubscriptionPeriod("Annually", false))
        assertEquals("Monthly", amazonSubscriptionPeriod("Monthly", true))
        assertNull(amazonSubscriptionPeriod(null, true))
    }
    companion object {
        private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        private val publicPem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(keys.public.encoded) + "\n-----END PUBLIC KEY-----"
    }
    private class Clock : BillingClock {
        var now = 1_800_000_000_000L; var uptime = 100_000L; var bootNumber = 10
        override fun wall() = now
        override fun elapsed() = uptime
        override fun boot() = bootNumber
        fun advance(ms: Long) { now += ms; uptime += ms }
    }
    private class Cache : LeaseStore {
        var value: CachedLease? = null; var fail = false; var failClear = false
        override fun read() = value
        override fun write(value: CachedLease): Boolean { if (fail) return false; this.value = value; return true }
        override fun clear() { if (!failClear) value = null }
    }
    private class Store : AmazonStore {
        var registered = false; var counter = 0; var userRequest = ""; var productRequest = ""; var updateRequest = ""; var buyRequest = ""
        var reset = true; val fulfilled = mutableListOf<String>(); lateinit var callback: (StoreEvent) -> Unit
        override fun register(listener: (StoreEvent) -> Unit) { registered = true; callback = listener }
        override fun user(): String { userRequest = "u${++counter}"; return userRequest }
        override fun product(sku: String): String { productRequest = "p${++counter}"; return productRequest }
        override fun updates(reset: Boolean): String { this.reset = reset; updateRequest = "r${++counter}"; return updateRequest }
        override fun purchase(sku: String): String { buyRequest = "b${++counter}"; return buyRequest }
        override fun fulfilled(receipt: String) { fulfilled += receipt }
    }
    private fun token(clock: Clock, user: String = "user", receipt: String = "receipt", environment: String = "production", audience: String = "com.trailnest.app", term: String = "annual", issued: Long = clock.now, expires: Long = issued + 100_000): String {
        val text = listOf("TN1", audience, environment, billingHash(user), billingHash(receipt), "parent", term, issued, expires).joinToString("\n")
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
        val signature = Signature.getInstance("SHA256withRSA").apply { initSign(keys.private); update(payload.toByteArray(Charsets.US_ASCII)) }.sign()
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
    }
    private inner class Fixture(enabled: Boolean = true) : AutoCloseable {
        val clock = Clock(); val cache = Cache(); val store = Store()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val guard = LeaseGuard(publicPem, "com.trailnest.app", "production", "parent", "annual", clock)
        var answer: suspend (String, String) -> ReceiptResult = { user, receipt -> ReceiptResult.Signed(token(clock, user, receipt)) }
        var serverReady = true
        val engine = BillingController(BillingConfig(enabled, "parent", "annual"), store, object : ReceiptVerifier {
            override suspend fun verify(user: String, receipt: String) = answer(user,receipt)
            override suspend fun ready() = serverReady
        }, guard, cache, scope)
        val receipt = StoreReceipt("receipt", "parent", "SUBSCRIPTION")
        fun identity(user: String = "user") { engine.start(); engine.refresh(); store.callback(StoreEvent.User(store.userRequest, user)) }
        fun product(offer: StoreOffer? = StoreOffer("annual", "QA PRICE", "SUBSCRIPTION", "Annual")) = store.callback(StoreEvent.Products(store.productRequest, offer))
        fun updates(values: List<StoreReceipt>, more: Boolean = false, user: String = "user", success: Boolean = true) = store.callback(StoreEvent.Updates(store.updateRequest, user, values, more, success))
        fun ready() { identity(); product(); updates(emptyList()) }
        fun purchase(status: String = "SUCCESSFUL", user: String = "user") { store.callback(StoreEvent.Purchase(store.buyRequest, user, receipt, status)) }
        override fun close() { scope.cancel() }
    }
    @Test fun disabledBuildNeverCallsAmazonOrGrants() { Fixture(false).use { f -> f.engine.start(); f.engine.refresh(); f.engine.buy(); assertFalse(f.store.registered); assertFalse(f.engine.state.value.configured); assertFalse(f.engine.state.value.premium) } }
    @Test fun metadataAndConfirmedAccountAndRestoreAreRequiredBeforeCheckout() { Fixture().use { f -> f.identity(); assertFalse(f.engine.state.value.canBuy); f.product(); assertFalse(f.engine.state.value.canBuy); f.updates(emptyList()); assertTrue(f.engine.state.value.canBuy); f.engine.buy(); assertTrue(f.engine.state.value.busy); assertFalse(f.engine.state.value.premium) } }
    @Test fun pendingDoesNotVerifyOrFulfillOrGrant() { Fixture().use { f -> f.ready(); var calls = 0; f.answer = { _, _ -> calls++; ReceiptResult.Denied }; f.engine.buy(); f.purchase("PENDING"); assertEquals(0, calls); assertTrue(f.engine.state.value.pending); assertFalse(f.engine.state.value.canBuy); assertFalse(f.engine.state.value.premium); assertTrue(f.store.fulfilled.isEmpty()) } }
    @Test fun unavailableServerPreventsCheckout() { Fixture().use { f -> f.ready(); f.serverReady = false; f.engine.buy(); assertEquals("",f.store.buyRequest); assertTrue(f.engine.state.value.problem); assertFalse(f.engine.state.value.canBuy) } }
    @Test fun cancelledCheckoutDoesNotInventSuccessOrCustomFailure() { Fixture().use { f -> f.ready(); f.engine.buy(); f.purchase("FAILED"); assertFalse(f.engine.state.value.premium); assertFalse(f.engine.state.value.problem); assertTrue(f.engine.state.value.canBuy) } }
    @Test fun unsupportedOrInvalidSkuCheckoutDisablesTheOffer() { for(status in listOf("INVALID_SKU","NOT_SUPPORTED","INACTIVE_BASE_SUBSCRIPTION")) Fixture().use { f -> f.ready(); f.engine.buy(); f.purchase(status); assertFalse(f.engine.state.value.premium); assertFalse(f.engine.state.value.canBuy); assertNull(f.engine.state.value.price); assertTrue(f.store.fulfilled.isEmpty()) } }
    @Test fun signedReceiptMustPersistBeforeFulfillment() { Fixture().use { f -> f.ready(); f.engine.buy(); f.purchase(); assertTrue(f.engine.state.value.premium); assertNotNull(f.cache.value); assertEquals(listOf("receipt"), f.store.fulfilled); assertFalse(f.engine.state.value.canBuy) } }
    @Test fun failedCacheWriteCannotFulfillOrGrant() { Fixture().use { f -> f.ready(); f.cache.fail = true; f.engine.buy(); f.purchase(); assertFalse(f.engine.state.value.premium); assertTrue(f.engine.state.value.problem); assertTrue(f.store.fulfilled.isEmpty()) } }
    @Test fun unsignedOrWrongReceiptResponseCannotGrant() { Fixture().use { f -> f.ready(); f.answer = { _, _ -> ReceiptResult.Signed(token(f.clock, receipt = "different")) }; f.engine.buy(); f.purchase(); assertFalse(f.engine.state.value.premium); assertTrue(f.store.fulfilled.isEmpty()) } }
    @Test fun restoreIsPaginatedAndReceiptOrderDoesNotRevokeANewerValidReceipt() { Fixture().use { f -> f.identity(); f.product(); f.answer = { u, r -> if(r == "old") ReceiptResult.Denied else ReceiptResult.Signed(token(f.clock,u,r)) }; f.updates(listOf(StoreReceipt("old","parent","SUBSCRIPTION")), more = true); assertFalse(f.store.reset); assertFalse(f.engine.state.value.premium); f.updates(listOf(f.receipt)); assertTrue(f.engine.state.value.premium); assertEquals(listOf("receipt"), f.store.fulfilled) } }
    @Test fun duplicateReceiptsVerifyOncePerRestore() { Fixture().use { f -> var calls = 0; f.answer = { u, r -> calls++; ReceiptResult.Signed(token(f.clock,u,r)) }; f.identity(); f.updates(listOf(f.receipt,f.receipt)); assertEquals(1,calls); assertEquals(1,f.store.fulfilled.size) } }
    @Test fun authoritativeEmptyRestoreClearsCache() { Fixture().use { f -> f.ready(); f.engine.buy(); f.purchase(); f.identity(); f.updates(emptyList()); /* Recent successful checkout is retained until RVS says otherwise. */ f.answer = { _, _ -> ReceiptResult.Denied }; f.identity(); f.updates(emptyList()); assertFalse(f.engine.state.value.premium) } }
    @Test fun coldStartEmptyHistoryClearsPreviouslyCachedLease() { Fixture().use { f -> f.cache.value = f.guard.checkpoint(f.guard.verify(token(f.clock),"user")!!); f.identity(); assertTrue(f.engine.state.value.premium); f.updates(emptyList()); assertFalse(f.engine.state.value.premium); assertNull(f.cache.value) } }
    @Test fun networkFailureUsesOnlyBoundUnexpiredCache() { Fixture().use { f -> f.cache.value = f.guard.checkpoint(f.guard.verify(token(f.clock),"user")!!); f.identity(); f.answer = { _, _ -> ReceiptResult.Retry }; f.updates(listOf(f.receipt)); assertTrue(f.engine.state.value.premium); f.clock.advance(100_000); f.engine.tick(); assertFalse(f.engine.state.value.premium); assertTrue(f.store.fulfilled.isEmpty()) } }
    @Test fun denialClearsMatchingCacheEvenWhenAnotherReceiptNeedsRetry() { Fixture().use { f -> f.cache.value = f.guard.checkpoint(f.guard.verify(token(f.clock),"user")!!); f.identity(); f.answer = { _, r -> if(r == "receipt") ReceiptResult.Denied else ReceiptResult.Retry }; f.updates(listOf(f.receipt,StoreReceipt("other","parent","SUBSCRIPTION"))); assertFalse(f.engine.state.value.premium) } }
    @Test fun revokedLeaseIsSuppressedEvenWhenDiskClearFails() { Fixture().use { f -> f.cache.value = f.guard.checkpoint(f.guard.verify(token(f.clock),"user")!!); f.cache.failClear = true; f.identity(); f.answer = { _, _ -> ReceiptResult.Denied }; f.updates(listOf(f.receipt)); assertNotNull(f.cache.value); assertFalse(f.engine.state.value.premium); f.engine.tick(); assertFalse(f.engine.state.value.premium) } }
    @Test fun accountSwitchRejectsCachedLeaseAndStaleUserCallback() { Fixture().use { f -> f.cache.value = f.guard.checkpoint(f.guard.verify(token(f.clock),"user")!!); f.identity(); val stale = f.store.userRequest; f.engine.refresh(); f.store.callback(StoreEvent.User(stale,"user")); assertFalse(f.engine.state.value.premium); f.store.callback(StoreEvent.User(f.store.userRequest,"different")); assertFalse(f.engine.state.value.premium); assertNull(f.cache.value) } }
    @Test fun failedUserLookupCannotUseLastUsersCache() { Fixture().use { f -> f.cache.value = f.guard.checkpoint(f.guard.verify(token(f.clock),"user")!!); f.engine.start(); f.engine.refresh(); f.store.callback(StoreEvent.User(f.store.userRequest,null)); assertFalse(f.engine.state.value.premium); assertFalse(f.engine.state.value.canBuy) } }
    @Test fun asyncOldAccountVerificationCannotWriteOrFulfillAfterSwitch() = runBlocking { Fixture().use { f -> val response = CompletableDeferred<ReceiptResult>(); f.answer = { _, _ -> response.await() }; f.identity(); f.updates(listOf(f.receipt)); f.identity("different"); response.complete(ReceiptResult.Signed(token(f.clock))); yield(); assertFalse(f.engine.state.value.premium); assertNull(f.cache.value); assertTrue(f.store.fulfilled.isEmpty()) } }
    @Test fun purchaseCallbackDuringResumeWaitsForCurrentIdentityAndHistory() { Fixture().use { f -> f.ready(); f.engine.buy(); f.engine.refresh(); f.purchase(); assertFalse(f.engine.state.value.premium); f.store.callback(StoreEvent.User(f.store.userRequest,"user")); f.updates(emptyList()); assertTrue(f.engine.state.value.premium) } }
    @Test fun foreignPurchaseCallbackCannotGrant() { Fixture().use { f -> f.ready(); f.engine.buy(); f.purchase(user = "different"); assertFalse(f.engine.state.value.premium); assertTrue(f.store.fulfilled.isEmpty()) } }
    @Test fun unknownSubscriptionPeriodOrPromotionsCannotBeSoldAsSimpleAnnualOffer() { for(offer in listOf(StoreOffer("annual","QA PRICE","SUBSCRIPTION","Monthly"), StoreOffer("annual","QA PRICE","SUBSCRIPTION","Annual",true),StoreOffer("wrong","QA PRICE","SUBSCRIPTION","Annual"),StoreOffer("annual","","SUBSCRIPTION","Annual"))) Fixture().use { f -> f.identity(); f.product(offer); f.updates(emptyList()); assertFalse(f.engine.state.value.canBuy); assertNull(f.engine.state.value.price) } }
    @Test fun networkUpdateFailurePreservesCacheButForeignAccountFailsClosed() { Fixture().use { f -> f.cache.value = f.guard.checkpoint(f.guard.verify(token(f.clock),"user")!!); f.identity(); f.updates(emptyList(),success = false); assertTrue(f.engine.state.value.premium); f.identity(); f.updates(emptyList(),user = "different"); assertFalse(f.engine.state.value.premium); assertTrue(f.engine.state.value.problem) } }
    @Test fun tokenSignatureEnvironmentAccountPackageSkuAndLifetimeAreAllRequired() { val c = Clock(); val guard = LeaseGuard(publicPem,"com.trailnest.app","production","parent","annual",c); assertNotNull(guard.verify(token(c),"user","receipt")); for(t in listOf(token(c)+"x",token(c,user="other"),token(c,environment="sandbox"),token(c,audience="com.trailnest.app.draft"),token(c,term="monthly"),token(c,issued=c.now+1),token(c,expires=c.now),token(c,expires=c.now+MAX_LEASE_MS+1))) assertNull(guard.verify(t,"user","receipt")) }
    @Test fun rebootRollbackAndClockTamperingInvalidateOfflineCache() { Fixture().use { f -> val lease = f.guard.verify(token(f.clock),"user")!!; val cached = f.guard.checkpoint(lease); assertNotNull(f.guard.cached(cached,"user")); f.clock.advance(1000); assertNotNull(f.guard.cached(cached,"user")); f.clock.bootNumber++; assertNull(f.guard.cached(cached,"user")); f.clock.bootNumber--; f.clock.now-=2000; assertNull(f.guard.cached(cached,"user")); f.clock.now+=100000; assertNull(f.guard.cached(cached,"user")) } }
}
