plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.dynatomic.afterglow"
    compileSdk = 36
    defaultConfig { applicationId = "com.dynatomic.afterglow"; minSdk = 26; targetSdk = 36; versionCode = 11; versionName = "1.0.0-android-preview.11" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs.getByName("debug") {
        System.getenv("AFTERGLOW_DEBUG_KEYSTORE")?.let { storeFile = file(it) }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies { testImplementation("junit:junit:4.13.2"); testImplementation("org.json:json:20250517") }
