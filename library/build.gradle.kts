@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "app.revanced"

kotlin {
    jvm()

    androidLibrary {
        namespace = "app.revanced.library"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":aidl"))
            implementation(libs.core.ktx)
            implementation(libs.libsu.nio)
            implementation(libs.libsu.service)
        }

        commonMain.dependencies {
            implementation(libs.apksig)
            implementation(libs.apkzlib)
            implementation(libs.bouncycastle.bcpkix)
            implementation(libs.bouncycastle.pgp)
            implementation(libs.sigstore.java)
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

    abiValidation {
        enabled = true
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexplicit-backing-fields",
            "-Xcontext-parameters",
        )
    }

    jvmToolchain(17)
}

mavenPublishing {
    publishing {
        repositories {
            maven {
                name = "githubPackages"
                url = uri("https://maven.pkg.github.com/revanced/revanced-library")
                credentials(PasswordCredentials::class)
            }
        }
    }

    signAllPublications()
    extensions.getByType<SigningExtension>().useGpgCmd()

    coordinates(group.toString(), project.name, version.toString())

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
