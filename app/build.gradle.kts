import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Release signing: put keystore.properties next to the root build file (never commit it) with
//   storeFile=../smrti-release.jks   storePassword=...   keyAlias=...   keyPassword=...
// or provide the same four values as SMRTI_STORE_FILE / SMRTI_STORE_PASSWORD / SMRTI_KEY_ALIAS / SMRTI_KEY_PASSWORD
// environment variables (used by CI). Without them the release build is simply left unsigned.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)

android {
    namespace = "com.smrti.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smrti.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        val storePath = signingValue("storeFile", "SMRTI_STORE_FILE")
        if (storePath != null) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "SMRTI_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SMRTI_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SMRTI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: shrinks code, removes unused resources, and makes startup and scrolling faster
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        // translations are provided for the main languages only; don't fail the build on the rest
        disable += "MissingTranslation"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // SQLite via Room (+ paging so long lists load a page at a time)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-paging:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.paging:paging-compose:3.3.5")

    implementation("androidx.core:core-splashscreen:1.0.1")

    // Installs the baseline profile (app/src/main/baseline-prof.txt) so the first launch is already compiled
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Image grid
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
