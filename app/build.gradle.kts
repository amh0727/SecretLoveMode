plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
}

android {
    namespace = "com.secretlovemode"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.secretlovemode"
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    aaptOptions{
        noCompress += ".task"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // TFLite 코어 런타임
    implementation("org.tensorflow:tensorflow-lite:2.13.0")
    // TFLite Task Library - Text용
    implementation("org.tensorflow:tensorflow-lite-task-text:0.4.0")
    // GPU Delegate
    implementation("org.tensorflow:tensorflow-lite-gpu:2.13.0")
    implementation("com.google.mediapipe:tasks-genai:0.10.25")
    implementation(libs.material)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

}