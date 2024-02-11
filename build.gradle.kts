plugins {
    kotlin("jvm") version "1.9.10"
    alias(libs.plugins.binary.compatibility.validator)
    `maven-publish`
}

group = "app.revanced"

repositories {
    mavenCentral()
    mavenLocal()
    maven { url = uri("https://jitpack.io") }
    google()
}

dependencies {
    implementation(libs.revanced.patcher)
    implementation(libs.kotlin.reflect)
    implementation(libs.jadb) // Fork with Shell v2 support.
    implementation(libs.jackson.module.kotlin)
    implementation(libs.apkzlib)
    implementation(libs.bcpkix.jdk15on)
    implementation(libs.guava)

    testImplementation(libs.revanced.patcher)
    testImplementation(libs.kotlin.test)
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("PASSED", "SKIPPED", "FAILED")
        }
    }
}

kotlin { jvmToolchain(11) }

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("gpr") {
            from(components["java"])

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
