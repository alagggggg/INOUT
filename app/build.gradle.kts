plugins { id("com.android.application") }

android {
    namespace = "com.quan.thuchi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.quan.thuchi"
        minSdk = 24
        targetSdk = 36
        versionCode = 17
        versionName = "1.6.1"
    }

    buildFeatures {
        viewBinding = false
        buildConfig = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.webkit:webkit:1.14.0")
}
