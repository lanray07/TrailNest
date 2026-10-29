package com.trailnest.app

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import com.trailnest.app.billing.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

class BillingDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    @Test fun unconfiguredRuntimeAndSdkManifestFailClosed() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertFalse(BuildConfig.BILLING_ENABLED)
        val billing=AmazonBillingRuntime.get(context)
        compose.runOnIdle { billing.start();billing.refresh();billing.buy();assertFalse(billing.hasPremium()) }
        assertFalse(billing.state.value.configured);assertNull(billing.state.value.price);assertFalse(billing.state.value.canBuy)
        val receiver=context.packageManager.getReceiverInfo(ComponentName(context.packageName,"com.amazon.device.iap.ResponseReceiver"),PackageManager.GET_META_DATA)
        assertTrue(receiver.exported);assertEquals("com.amazon.inapp.purchasing.Permission.NOTIFY",receiver.permission)
    }
    @Test fun nodeSignedFixtureVerifiesWithAndroidCryptoButCannotCrossAccountOrEnvironment() {
        val assets=InstrumentationRegistry.getInstrumentation().context.assets
        val public=assets.open("billing-fixture/public.pem").bufferedReader().use { it.readText() }
        val token=assets.open("billing-fixture/lease.txt").bufferedReader().use { it.readText().trim() }
        val clock=object:BillingClock {override fun wall()=1800000000000L;override fun elapsed()=1000L;override fun boot()=1}
        val guard=LeaseGuard(public,"com.trailnest.app.draft","sandbox","qa.parent","qa.annual",clock)
        assertNotNull(guard.verify(token,"QA-user","QA-receipt"));assertNull(guard.verify(token,"other","QA-receipt"));assertNull(guard.verify(token,"QA-user","other"))
        assertNull(LeaseGuard(public,"com.trailnest.app.draft","production","qa.parent","qa.annual",clock).verify(token,"QA-user","QA-receipt"))
    }
    data class Case(val tag:String,val width:Int)
    @Test fun configuredBillingDraftLayoutsAndControlStates() {
        Assume.assumeTrue(BuildConfig.APPLICATION_ID.endsWith(".draft"))
        prepareTestWindow { compose.activity }
        val instrumentation=InstrumentationRegistry.getInstrumentation();val app=instrumentation.targetContext
        val output=File(app.getExternalFilesDir(null),"billing-checks").apply { mkdirs() }
        val tags=AppLanguage.supportedTags(InstrumentationRegistry.getInstrumentation().targetContext)
        val cases=tags.map { Case(it,360) }+listOf("ar","de","ja","hi").map { Case(it,800) }
        var current by mutableStateOf(cases.first());var state by mutableStateOf(BillingState())
        var buyCalls=0;var restoreCalls=0
        val overflow=ConcurrentLinkedQueue<String>();val results=JSONArray();val failures=mutableListOf<String>()
        compose.setContent {
            val host=LocalContext.current;val hostConfig=LocalConfiguration.current;val density=LocalDensity.current.density
            val config=remember(hostConfig,current) { Configuration(hostConfig).apply { setLocale(Locale.forLanguageTag(current.tag));setLayoutDirection(Locale.forLanguageTag(current.tag));fontScale=2f;screenWidthDp=current.width;screenHeightDp=640 } }
            val context=remember(config) { android.view.ContextThemeWrapper(host,R.style.Theme_TrailNest).apply { applyOverrideConfiguration(config) } }
            key(config) { AndroidView(factory={ ComposeView(context).apply { setContent {
                CompositionLocalProvider(LocalContext provides context,LocalConfiguration provides config,LocalDensity provides Density(density,2f),LocalLayoutDirection provides if(current.tag=="ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalTextLayoutAudit provides { text,layout -> if(layout.hasVisualOverflow)overflow.add(text.take(100)) }) {
                    MaterialTheme { Box(Modifier.requiredSize(current.width.dp,640.dp)) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { BillingPanel(state,{buyCalls++},{restoreCalls++}) } } }
                }
            } } },modifier=Modifier.requiredSize(current.width.dp,640.dp)) }
        }
        val modes=listOf("disabled","ready","checking","pending","active","unverified","no-offer")
        for(case in cases) {
            val checks=JSONArray()
            for(mode in modes) {
                try {
                    compose.runOnIdle { overflow.clear();current=case;state=when(mode) {
                        "ready"->BillingState(configured=true,price="QA PRICE",canBuy=true)
                        "checking"->BillingState(configured=true,price="QA PRICE",busy=true)
                        "pending"->BillingState(configured=true,pending=true)
                        "active"->BillingState(configured=true,premiumUntil=1800000010000)
                        "unverified"->BillingState(configured=true,price="QA PRICE",problem=true)
                        "no-offer"->BillingState(configured=true)
                        else->BillingState()
                    } }
                    compose.waitForIdle()
                    if(mode=="disabled") { compose.onNodeWithTag("billing-subscribe").assertDoesNotExist();compose.onNodeWithTag("billing-restore").assertDoesNotExist() }
                    else {
                        compose.onNodeWithTag("billing-restore").performScrollTo().assertIsDisplayed()
                        if(mode=="checking") compose.onNodeWithTag("billing-restore").assertIsNotEnabled() else compose.onNodeWithTag("billing-restore").assertIsEnabled()
                        when(mode) {
                            "ready"->{ val before=buyCalls;compose.onNodeWithTag("billing-subscribe").performScrollTo().assertIsEnabled().performClick();compose.runOnIdle { assertEquals(before+1,buyCalls) };val restored=restoreCalls;compose.onNodeWithTag("billing-restore").performScrollTo().performClick();compose.runOnIdle { assertEquals(restored+1,restoreCalls) } }
                            "active","pending"->compose.onNodeWithTag("billing-subscribe").assertDoesNotExist()
                            else->compose.onNodeWithTag("billing-subscribe").assertIsNotEnabled()
                        }
                        if(mode!="checking") checks.put(compose.auditAccessibleControls(app.resources.displayMetrics.density).put("mode",mode))
                    }
                    assertTrue("Text overflow: ${overflow.joinToString()}",overflow.isEmpty())
                } catch(error:Throwable) { failures+="${case.tag}-${case.width}/$mode: ${error.message}" }
            }
            results.put(JSONObject().put("locale",case.tag).put("widthDp",case.width).put("heightDp",640).put("fontScale",2).put("syntheticOffer","QA PRICE").put("states",JSONArray(modes)).put("controlAudits",checks))
            instrumentation.sendStatus(2,android.os.Bundle().apply { putString("stream","Billing UI: ${case.tag}-${case.width} complete\n") })
            File(output,"matrix.json").writeText(JSONObject().put("configurations",results).put("failures",JSONArray(failures)).toString(2))
        }
        assertTrue(failures.take(12).joinToString("\n"),failures.isEmpty())
    }
}
