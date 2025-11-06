import com.android.build.api.dsl.Packaging
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.2.20-2.0.4"
}

android {
    namespace = "com.iamashad.musesample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.iamashad.musesample"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }// pickFirsts.add("**/*.onnx")

    // pickFirsts.add("**/arm64-v8a/libonnxruntime.so")
    fun Packaging.() {
        jniLibs {
            pickFirsts.add("**/libonnxruntime.so")
            // pickFirsts.add("**/arm64-v8a/libonnxruntime.so")
        }
        resources {
            pickFirsts.add("assets/ml/pcg_segmentation.onnx")
            // pickFirsts.add("**/*.onnx")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    //muse sdk
    implementation(files("libs/surr-core.aar"))

    //ort mobile
    implementation("com.microsoft.onnxruntime:onnxruntime-android:latest.release")

    //room
    implementation("androidx.room:room-runtime:2.8.2")
    implementation("androidx.room:room-ktx:2.8.2")
    ksp("androidx.room:room-compiler:2.8.2")

    //sqlcipher
    implementation("net.zetetic:sqlcipher-android:4.11.0")
    implementation("androidx.sqlite:sqlite:2.6.1")

    //compose navigation
    implementation("androidx.navigation:navigation-compose:2.9.5")

    // Jetpack Security for EncryptedSharedPreferences (stores DB key; Keystore-backed)
    implementation("androidx.security:security-crypto:1.1.0")

    //webkit
    implementation("androidx.webkit:webkit:1.14.0")
    //lottie
    implementation("com.airbnb.android:lottie-compose:6.6.9")
    //icon pack
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    //mpandroidchart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    //coroutines
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    //webview
    implementation("androidx.webkit:webkit:1.14.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}