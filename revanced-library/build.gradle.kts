plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.binary.compatibility.validator)
    `maven-publish`
    signing
}

group = "app.revanced"

kotlin {
    jvm()

    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
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
            implementation(libs.jadb) // Updated fork
            implementation(libs.bcpkix.jdk15on)
            implementation(libs.jackson.module.kotlin)
            implementation(libs.apkzlib)
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
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/revanced/revanced-patcher")
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
