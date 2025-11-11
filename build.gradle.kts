plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.ambulanceroutemvp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ambulanceroutemvp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
}

dependencies {
    dependencies {
        implementation("androidx.core:core-ktx:1.15.0")
        implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
        implementation("androidx.activity:activity-compose:1.9.2")

        val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
        implementation(composeBom)
        implementation("androidx.compose.ui:ui")
        implementation("androidx.compose.ui:ui-tooling-preview")
        implementation("androidx.compose.material3:material3")
        debugImplementation("androidx.compose.ui:ui-tooling")

        // 🗺 OpenStreetMap (osmdroid)
        implementation("org.osmdroid:osmdroid-android:6.1.18")

        // For location (optional)
        dependencies {
            implementation("androidx.activity:activity-compose:1.9.2")
            implementation("androidx.compose.ui:ui:1.7.3")
            implementation("androidx.compose.material3:material3:1.3.0")
            implementation("androidx.compose.ui:ui-tooling-preview:1.7.3")
            implementation("com.google.android.gms:play-services-location:21.3.0")
            implementation("org.osmdroid:osmdroid-android:6.1.18")
            implementation("com.opencsv:opencsv:5.9")
            implementation("com.squareup.okhttp3:okhttp:4.12.0")
            implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
        }


    }
}

