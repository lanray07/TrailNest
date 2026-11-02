import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val billingEnabled = providers.gradleProperty("trailnestBillingEnabled").getOrElse("false") == "true"
val billingEnvironment = providers.gradleProperty("trailnestBillingEnvironment").getOrElse("production")
val billingParent = providers.gradleProperty("trailnestBillingParentSku").getOrElse("")
val billingAnnual = providers.gradleProperty("trailnestBillingAnnualSku").getOrElse("")
val billingUrl = providers.gradleProperty("trailnestBillingVerificationUrl").getOrElse("")
val latBillingEnabled = providers.gradleProperty("trailnestLatBillingEnabled").getOrElse("false") == "true"
val latBillingUrl = "https://trailnest-billing-lat.vercel.app/v1/amazon/verify"
// Optional operator-provided production credentials. No keystore is generated,
// and an unconfigured release continues to build unsigned for local checks.
val releaseSigningNames = listOf("TRAILNEST_RELEASE_KEYSTORE", "TRAILNEST_RELEASE_STORE_PASSWORD", "TRAILNEST_RELEASE_KEY_ALIAS", "TRAILNEST_RELEASE_KEY_PASSWORD")
val releaseSigningValues = releaseSigningNames.associateWith { providers.environmentVariable(it).orNull }
val releaseSigningConfigured = releaseSigningValues.values.any { it != null }
if (releaseSigningConfigured) {
    require(releaseSigningValues.values.all { !it.isNullOrBlank() }) { "Set all four TRAILNEST_RELEASE signing environment variables, or unset all four for an unsigned local build" }
    require(file(releaseSigningValues.getValue("TRAILNEST_RELEASE_KEYSTORE")!!).isFile) { "Configured production release keystore is unavailable" }
}
fun billingLiteral(value: String): String {
    require(value.none { it.code < 32 }) { "Invalid billing build setting" }
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
require(billingEnvironment in listOf("production", "sandbox", "lat"))
if (latBillingEnabled) {
    require(file("src/main/assets/AppstoreAuthenticationKey.pem").isFile && file("src/lat/assets/billing-verifier-public.pem").isFile) { "Configure the independent LAT public keys" }
}
if (billingEnabled) {
    require(billingParent.matches(Regex("[A-Za-z0-9._-]{1,200}")) && billingAnnual.matches(Regex("[A-Za-z0-9._-]{1,200}"))) { "Configure actual Amazon subscription SKUs" }
    val endpoint = URI(billingUrl)
    require(endpoint.scheme == "https" && !endpoint.host.isNullOrBlank() && endpoint.userInfo == null && endpoint.query == null && endpoint.fragment == null && endpoint.path.endsWith("/v1/amazon/verify")) { "Configure a fixed HTTPS URL ending in /v1/amazon/verify" }
    require(file("src/main/assets/AppstoreAuthenticationKey.pem").isFile && file("src/main/assets/billing-verifier-public.pem").isFile) { "Configure Amazon and verifier PUBLIC keys" }
    require(billingEnvironment == "production" || gradle.startParameter.taskNames.none { it.contains("release", ignoreCase = true) }) { "Release builds cannot accept test entitlements" }
    if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
        require(file("src/release/assets/billing-verifier-public.pem").isFile) { "A separate production verifier pin is required" }
        require(file("src/release/assets/billing-verifier-public.pem").readText().trim() != file("src/main/assets/billing-verifier-public.pem").readText().trim()) { "Production cannot reuse the sandbox verifier identity" }
        require(billingUrl == "https://trailnest-billing-production.lanraybanks.workers.dev/v1/amazon/verify" && billingParent == "com.trailnest.premium.production" && billingAnnual == "com.trailnest.premium.production.annual") { "Production billing requires the fixed production host and annual SKUs" }
    }
}
android {
    namespace = "com.trailnest.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.trailnest.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 16
        versionName = "0.8.7"
        buildConfigField("boolean", "LAT_BUILD", "false")
        buildConfigField("boolean", "BILLING_ENABLED", billingEnabled.toString())
        buildConfigField("String", "BILLING_ENVIRONMENT", billingLiteral(billingEnvironment))
        buildConfigField("String", "BILLING_PARENT_SKU", billingLiteral(billingParent))
        buildConfigField("String", "BILLING_ANNUAL_SKU", billingLiteral(billingAnnual))
        buildConfigField("String", "BILLING_VERIFICATION_URL", billingLiteral(billingUrl))
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    testBuildType = providers.gradleProperty("trailnestTestBuildType").getOrElse("debug")
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (releaseSigningConfigured) create("production") {
            storeFile = file(releaseSigningValues.getValue("TRAILNEST_RELEASE_KEYSTORE")!!)
            storePassword = releaseSigningValues.getValue("TRAILNEST_RELEASE_STORE_PASSWORD")
            keyAlias = releaseSigningValues.getValue("TRAILNEST_RELEASE_KEY_ALIAS")
            keyPassword = releaseSigningValues.getValue("TRAILNEST_RELEASE_KEY_PASSWORD")
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        release {
            signingConfig = if (releaseSigningConfigured) signingConfigs.getByName("production") else null
            isMinifyEnabled = false
            buildConfigField("boolean", "BILLING_ENABLED", (billingEnabled && billingEnvironment == "production").toString())
        }
        create("translationPreview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".draft"
            versionNameSuffix = "-draft"
            matchingFallbacks += "debug"
        }
        create("lat") {
            initWith(getByName("release"))
            isDebuggable = false
            signingConfig = null
            versionNameSuffix = "-lat"
            matchingFallbacks += "release"
            resValue("string", "lat_app_name", "TrailNest LAT")
            buildConfigField("boolean", "LAT_BUILD", "true")
            buildConfigField("boolean", "BILLING_ENABLED", latBillingEnabled.toString())
            buildConfigField("String", "BILLING_ENVIRONMENT", billingLiteral("lat"))
            buildConfigField("String", "BILLING_PARENT_SKU", billingLiteral("com.trailnest.premium"))
            buildConfigField("String", "BILLING_ANNUAL_SKU", billingLiteral("com.trailnest.premium_term"))
            buildConfigField("String", "BILLING_VERIFICATION_URL", billingLiteral(latBillingUrl))
        }
    }
}
val catalogueAssets = layout.buildDirectory.dir("generated/catalogueAssets")
val buildCatalogue by tasks.registering(Exec::class) {
    workingDir(rootProject.projectDir)
    commandLine("node", "tools/build-catalogue.mjs", catalogueAssets.get().asFile.absolutePath)
    inputs.files(rootProject.file("content/routes.json"), rootProject.file("config/editorial-policy.json"), rootProject.file("tools/editorial-review.mjs"), rootProject.file("tools/build-catalogue.mjs"))
    outputs.dir(catalogueAssets)
}
android.sourceSets.getByName("main").assets.srcDir(catalogueAssets)
tasks.named("preBuild").configure { dependsOn(buildCatalogue) }
dependencies {
    // Pin the stable SDK compatible with this project's Kotlin 2.1 toolchain.
    implementation("org.maplibre.gl:android-sdk:11.8.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.amazon.device:amazon-appstore-sdk:3.0.9")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    add("translationPreviewImplementation", "androidx.compose.ui:ui-test-manifest")
}
