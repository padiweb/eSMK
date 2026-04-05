plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.smkaltan.ujian"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smkaltan.ujian"
        minSdk = 29        // Android 12
        targetSdk = 35     // Android 15
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signing config untuk release build
            // signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
    }

    // Disable split screen (tambahan keamanan)
    defaultConfig {
        manifestPlaceholders["resizeableActivity"] = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // WebKit untuk WebView yang lebih aman
    implementation("androidx.webkit:webkit:1.12.1")

    // Lifecycle untuk foreground service
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
