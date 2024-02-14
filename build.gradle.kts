plugins {
    alias(libs.plugins.androidLibrary).apply(false)
    alias(libs.plugins.kotlinMultiplatform).apply(false)
    `maven-publish`
    signing
}

subprojects {
    // Because access to the project is necessary to authenticate with GitHub,
    // the following block must be placed in the root build.gradle.kts file
    // instead of the settings.gradle.kts file inside the dependencyResolutionManagement block.
    repositories {
        mavenCentral()
        mavenLocal()
        google()
        maven { url = uri("https://jitpack.io") }
        maven {
            // A repository must be speficied for some reason. "registry" is a dummy.
            url = uri("https://maven.pkg.github.com/revanced/registry")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
