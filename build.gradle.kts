plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.binary.compatibility.validator) apply false
    alias(libs.plugins.ktor) apply false
}

group = "app.revanced"

subprojects {
    // Because access to the project is necessary to authenticate with GitHub,
    // the following block must be placed in the root build.gradle.kts file
    // instead of the settings.gradle.kts file inside the dependencyResolutionManagement block.
    repositories {
        mavenCentral()
        mavenLocal()
        google()
        maven {
            // A repository must be specified for some reason. "registry" is a dummy.
            url = uri("https://maven.pkg.github.com/revanced/registry")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
        maven { url = uri("https://jitpack.io") }
    }
}
