plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.binary.compatibility.validator)
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
    maven { url = uri("https://jitpack.io") }
    maven {
        // A repository must be specified for some reason. "registry" is a dummy.
        url = uri("https://maven.pkg.github.com/revanced/registry")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
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
            implementation(libs.libsu.nio)
            implementation(libs.libsu.service)
            implementation(libs.core.ktx)
        }

        commonMain.dependencies {
            implementation(libs.revanced.patcher)
            implementation(libs.kotlin.reflect)
            implementation(libs.jadb) // Fork with Shell v2 support.
            implementation(libs.bcpkix.jdk15on)
            implementation(libs.jackson.module.kotlin)
            implementation(libs.apkzlib)
            implementation(libs.apksig)
            implementation(libs.guava)
        }

        commonTest.dependencies {
            implementation(libs.revanced.patcher)
            implementation(libs.kotlin.test.junit)
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
