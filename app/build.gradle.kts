plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sendmefile77.gamebroth"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sendmefile77.gamebroth"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_shared")
                System.getenv("SPIRV_HEADERS_CMAKE_DIR")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { arguments += "-DSPIRV-Headers_DIR=$it" }
            }
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(project(":native-text"))
    implementation(project(":core:model"))
    implementation(project(":core:storage"))
    implementation(project(":core:simulation"))
    implementation(project(":core:ai-text"))
    implementation(project(":core:ai-image"))
    implementation(project(":core:adult-contracts"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
