import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Single source of truth for the app version: the VERSION file in the repo root (MAJOR.MINOR.PATCH).
val appVersionName: String = rootProject.file("VERSION").readText().trim()
val appVersionCode: Int = run {
    val parts = appVersionName.split(".").map { it.toInt() }
    require(parts.size == 3 && parts.all { it in 0..99 }) {
        "VERSION must be MAJOR.MINOR.PATCH with each part 0..99, got '$appVersionName'"
    }
    parts[0] * 10000 + parts[1] * 100 + parts[2]
}

// Release signing comes from keystore.properties (local) or environment variables (CI).
// Without either, the release build is produced unsigned.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "ABHANG_KEYSTORE_FILE")

android {
    namespace = "com.abhang.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.abhang.app"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "ABHANG_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "ABHANG_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "ABHANG_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.navigation:navigation-compose:2.10.1")
    implementation("androidx.compose.ui:ui:1.12.1")
    implementation("androidx.compose.foundation:foundation:1.12.1")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}
