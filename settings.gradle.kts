enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

buildCache {
    local {
        isEnabled = "CI" !in System.getenv()
    }
}

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://jitpack.io") }
        google()
    }
}

// TODO: Figure out why this causes problems: rootProject.name = "revanced-library"

include(":revanced-library")
