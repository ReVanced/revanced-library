// TODO: Figure out why this causes problems.
rootProject.name = "revanced-library"

buildCache {
    local {
        isEnabled = "CI" !in System.getenv()
    }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
    }
}
