package com.trailnest.app

import androidx.test.platform.app.InstrumentationRegistry
import com.trailnest.app.billing.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in network contract check. Does not call Amazon checkout or grant Premium. */
class BillingReadinessDeviceTest {
    @Test fun shippedVerifierCanReachProductionReadiness() = runBlocking {
        assumeTrue(BuildConfig.APPLICATION_ID.endsWith(".draft"))
        val endpoint = InstrumentationRegistry.getArguments().getString("billingVerificationUrl")
        assumeTrue("Supply the configured production verification URL explicitly", endpoint != null)
        require(endpoint == "https://trailnest-billing-production.lanraybanks.workers.dev/v1/amazon/verify")
        val verifier = HttpsReceiptVerifier(endpoint!!)
        assertTrue("The shipped Android readiness path must respond 204 before checkout", verifier.ready())
        val checkout = CompletableDeferred<String>()
        lateinit var callback: (StoreEvent) -> Unit
        val store = object : AmazonStore {
            override fun register(listener: (StoreEvent) -> Unit) { callback = listener }
            override fun user() = "qa-user-request"
            override fun product(sku: String) = "qa-product-request"
            override fun updates(reset: Boolean) = "qa-restore-request"
            // Observe the handoff without launching or making a real purchase.
            override fun purchase(sku: String): String { checkout.complete(sku); return "qa-purchase-request" }
            override fun fulfilled(receipt: String) { error("No receipt exists in this test") }
        }
        val clock = object : BillingClock {
            override fun wall() = System.currentTimeMillis()
            override fun elapsed() = android.os.SystemClock.elapsedRealtime()
            override fun boot() = 1
        }
        val publicKey = InstrumentationRegistry.getInstrumentation().context.assets
            .open("billing-fixture/public.pem").bufferedReader().use { it.readText() }
        val parent = "com.trailnest.premium.production"
        val annual = "com.trailnest.premium.production.annual"
        val guard = LeaseGuard(publicKey, "com.trailnest.app.draft", "production", parent, annual, clock)
        val cache = object : LeaseStore {
            override fun read(): CachedLease? = null
            override fun write(value: CachedLease): Boolean = error("No entitlement may be written")
            override fun clear() {}
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val engine = BillingController(BillingConfig(true, parent, annual), store, verifier, guard, cache, scope)
            engine.start(); engine.refresh()
            callback(StoreEvent.User("qa-user-request", "qa-local-user"))
            callback(StoreEvent.Products("qa-product-request", StoreOffer(annual, "QA PRICE", "SUBSCRIPTION", "Annual")))
            callback(StoreEvent.Updates("qa-restore-request", "qa-local-user", emptyList(), false, true))
            assertTrue(engine.state.value.canBuy)
            engine.buy()
            assertEquals(annual, withTimeout(12_000) { checkout.await() })
            assertFalse(engine.state.value.problem)
            assertFalse(engine.state.value.premium)
        } finally { scope.cancel() }
    }
}
