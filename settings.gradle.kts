buildCache {
    local {
        isEnabled = "CI" !in System.getenv()
    }
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

include(":library", ":library-networking")
