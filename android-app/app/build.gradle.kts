import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.xrtm8.athar"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.xrtm8.athar"
        minSdk = 26
        targetSdk = 34
        versionCode = 57
        versionName = "4.1.0-LTS"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.clear()
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
        }
    }

    signingConfigs {
        create("release") {
            val keystorePropsFile = rootProject.file("../keystore/keystore.properties").takeIf { it.exists() }
                ?: rootProject.file("keystore/keystore.properties").takeIf { it.exists() }
            val keystoreProperties = Properties()
            if (keystorePropsFile != null) {
                FileInputStream(keystorePropsFile).use { keystoreProperties.load(it) }
            }

            val keystorePath = System.getenv("ATHAR_KEYSTORE_PATH")
                ?: keystoreProperties.getProperty("storeFile")
                ?: "../../keystore/athar-release.jks"

            val ksFile = file(keystorePath)
            if (ksFile.exists()) {
                storeFile = ksFile
            }

            storePassword = System.getenv("ATHAR_KEYSTORE_PASSWORD")
                ?: keystoreProperties.getProperty("storePassword")
                ?: ""

            keyAlias = System.getenv("ATHAR_KEY_ALIAS")
                ?: keystoreProperties.getProperty("keyAlias")
                ?: "athar_release"

            keyPassword = System.getenv("ATHAR_KEY_PASSWORD")
                ?: keystoreProperties.getProperty("keyPassword")
                ?: ""

            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    debugImplementation(libs.androidx.ui.tooling)
}
