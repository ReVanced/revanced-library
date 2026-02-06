plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.revanced.library"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig.minSdk = libs.versions.android.minSdk.get().toInt()

    buildFeatures.aidl = true
}