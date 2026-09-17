plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// GitHub Actions debug builds must always move forward so Android can install them as updates.
// Local builds keep a small fallback code for developer convenience.
val gameBrothVersionCode = System.getenv("GITHUB_RUN_NUMBER")
    ?.toIntOrNull()
    ?.let { 1000 + it }
    ?: 17

android {
    namespace = "com.sendmefile77.gamebroth"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sendmefile77.gamebroth"
        minSdk = 26
        targetSdk = 35
        versionCode = gameBrothVersionCode
        versionName = "1.0.1"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // The embedded Local Dream core is an executable stored under lib/arm64-v8a.
    // Legacy packaging makes Android extract it to applicationInfo.nativeLibraryDir,
    // where ProcessBuilder can execute it directly.
    packaging {
        jniLibs {
            useLegacyPackaging = true
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
