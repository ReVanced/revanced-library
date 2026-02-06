rootProject.name = "revanced-library"

pluginManagement {
    repositories {
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven {
            name = "githubPackages"
            url = uri("https://maven.pkg.github.com/revanced/revanced-library")
            credentials(PasswordCredentials::class)
        }
        maven { url = uri("https://jitpack.io") }
    }
}

include(":library", ":aidl")
