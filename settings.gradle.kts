rootProject.name = "revanced-library"

buildCache {
    local {
        isEnabled = !System.getenv().containsKey("CI")
    }
}
