import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "com.zoloft.innovv.app")
            freeCompilerArgs += listOf(
                "-Xoverride-konan-properties=" +
                    "osVersionMin.ios_arm64=18.0;" +
                    "osVersionMin.ios_simulator_arm64=18.0",
            )
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("com.zoloft.innovv:innovv-sdk:0.1.0")
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.coil.compose)
            implementation(libs.multiplatform.settings)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.media3.exoplayer)
            implementation(libs.androidx.media3.exoplayer.rtsp)
            implementation(libs.androidx.media3.ui)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.zoloft.innovv.app"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.zoloft.innovv.app"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.zoloft.innovv.app.resources"
}
