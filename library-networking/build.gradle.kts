import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.ktor)
    `maven-publish`
    signing
}

dependencies {
    implementation(project(":library"))
    implementation(libs.revanced.patcher)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.caching.headers)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.koin.ktor)
    implementation(libs.logback.classic)
}

tasks {
    processResources {
        expand("projectVersion" to project.version)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
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
        create<MavenPublication>("revanced-library-networking-publication") {
            version = project.version.toString()

            pom {
                name = "ReVanced Networking Library"
                description = "Library to interface to common utilities for ReVanced over a network."
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
    sign(publishing.publications["revanced-library-networking-publication"])
}
