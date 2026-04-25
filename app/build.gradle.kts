plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.innoasis.y1syncer"
    compileSdk {
        version = release(34)
    }

    defaultConfig {
        applicationId = "io.innoasis.y1syncer"
        minSdk = 17
        targetSdk = 22
        versionCode = 1
        versionName = "0.1.0-stage1"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.nanohttpd)
    implementation(libs.jcifs.ng)
    implementation(libs.androidx.multidex)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}