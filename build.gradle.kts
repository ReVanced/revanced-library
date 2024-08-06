plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
    signing
}

group = "app.revanced"

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

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = JavaVersion.VERSION_11.toString()
            }
        }
    }

    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = JavaVersion.VERSION_11.toString()
            }
        }

        publishLibraryVariants("release")
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.core.ktx)
            implementation(libs.libsu.nio)
            implementation(libs.libsu.service)
        }

        commonMain.dependencies {
            implementation(libs.apksig)
            implementation(libs.apkzlib)
            implementation(libs.bcpkix.jdk15on)
            implementation(libs.guava)
            implementation(libs.jadb)
            implementation(libs.kotlin.reflect)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.revanced.patcher)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test.junit)
            implementation(libs.revanced.patcher)
        }
    }
}

android {
    namespace = "app.revanced.library"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_11
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/revanced/revanced-library")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }

    publications {
        create<MavenPublication>("revanced-library-publication") {
            version = project.version.toString()

            pom {
                name = "ReVanced Library"
                description = "Library containing common utilities for ReVanced"
                url = "https://revanced.app"

                licenses {
                    license {
                        name = "GNU General Public License v3.0"
                        url = "https://www.gnu.org/licenses/gpl-3.0.en.html"
                    }
                }

                developers {
                    developer {
                        id = "ReVanced"
                        name = "ReVanced"
                        email = "contact@revanced.app"
                    }
                }

                scm {
                    connection = "scm:git:git://github.com/revanced/revanced-library.git"
                    developerConnection = "scm:git:git@github.com:revanced/revanced-library.git"
                    url = "https://github.com/revanced/revanced-library"
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["revanced-library-publication"])
}
