import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// Secrets (Telegram API keys, signing) live in secrets.properties — gitignored.
// See secrets.properties.example for the expected format.
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "su.kirian.wearayugram"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "su.kirian.wearayugram"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("int", "TG_API_ID", secrets.getProperty("TG_API_ID", "0"))
        buildConfigField("String", "TG_API_HASH", "\"${secrets.getProperty("TG_API_HASH", "")}\"")
    }

    val hasSigning = secrets.containsKey("RELEASE_STORE_FILE")
    if (hasSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(secrets.getProperty("RELEASE_STORE_FILE"))
                storePassword = secrets.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = secrets.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = secrets.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    lint {
        // False positive: we use ComponentActivity (no fragments anywhere); the check
        // fires off an old transitive androidx.fragment version.
        disable += "InvalidFragmentVersionForActivityResult"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    kotlin {
        jvmToolchain(11)
    }

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
}

dependencies {
    // TDLib: place td-android.jar in app/libs/ and libtdjni.so in app/src/main/jniLibs/arm64-v8a/
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)
    implementation(libs.compose.ui.tooling)
    implementation(libs.core.splashscreen)
    implementation(libs.play.services.wearable)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)

    implementation(libs.coroutines.android)
    implementation(libs.coroutines.guava)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.zxing.core)
    implementation(libs.wear.input)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.wear.tiles)
    implementation(libs.wear.tiles.material)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)
}
