import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) load(f.inputStream())
}

// Developer-supplied secrets live in `local.properties` (gitignored) so they
// never hit the repo. Fall back to placeholder literals so the build still
// succeeds on a fresh clone — the app checks for these at runtime and shows
// a clear "not configured" message if they're unset.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
fun secret(key: String, fallback: String): String =
    (localProps.getProperty(key) ?: System.getenv(key) ?: fallback)

android {
    namespace = "com.claudeagent.phone"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.claudeagent.phone"
        minSdk = 30
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"

        // These ship into BuildConfig.kt at compile time. See local.properties
        // for where to put real values. Safe-by-default placeholders make a
        // fresh clone buildable without any secret-setup first.
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${secret("SUPABASE_URL", "https://YOUR_PROJECT.supabase.co")}\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${secret("SUPABASE_ANON_KEY", "YOUR_ANON_KEY")}\"",
        )
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${secret("GOOGLE_WEB_CLIENT_ID", "")}\"",
        )
        buildConfigField(
            "String",
            "SENTRY_DSN",
            "\"${secret("SENTRY_DSN", "")}\"",
        )
    }

    signingConfigs {
        create("release") {
            val storeFileName = keystoreProps.getProperty("storeFile")
            if (storeFileName != null) {
                storeFile = file(storeFileName)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Google sign-in via Credential Manager (unified Android auth API).
    // Fine on API 30+ (we're minSdk 30); the play-services-auth adapter is
    // what makes Google-branded sign-in appear in the bottom sheet.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Crash reporting. Sentry auto-captures uncaught exceptions + ANRs
    // as soon as Sentry.init() runs with a real DSN. DSN-gated in
    // [HandyAIApplication] so debug builds without a DSN stay silent.
    implementation("io.sentry:sentry-android:7.14.0")
}
