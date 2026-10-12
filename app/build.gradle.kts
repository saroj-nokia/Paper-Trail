import java.util.zip.ZipFile

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "com.example"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.aistudio.papertrail.ptvlt"
    minSdk = 31
    targetSdk = 37
    versionCode = 2
    versionName = "1.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val keystoreFile = file(keystorePath)
      val isReleaseRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
      if (isReleaseRequested && !keystoreFile.exists()) {
        throw GradleException(
          "Release keystore not found at '$keystorePath'. Set the KEYSTORE_PATH environment " +
          "variable to point at a valid release keystore, or place one at " +
          "'${rootDir}/my-upload-key.jks'. Release builds must never fall back to the debug keystore."
        )
      }
      storeFile = keystoreFile
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      isCrunchPngs = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  // Googlebook: Keep every ABI shipped by dependencies (do NOT add ndk.abiFilters or splits.abi).
  // arm64-v8a (Qualcomm) and x86_64 (Intel/AMD) must both stay available for Googlebook.
  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.documentfile)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)

  // Compose UI & Material 3
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)

  // Room Persistence
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  "ksp"(libs.androidx.room.compiler)

  // Security: SQLCipher & Biometric
  implementation(libs.sqlcipher.android)
  implementation(libs.androidx.sqlite)
  implementation(libs.androidx.biometric)

  // On-Device ML Kit Text Recognition (OCR)
  implementation(libs.mlkit.text.recognition)

  // Background Reminders (WorkManager)
  implementation(libs.androidx.work.runtime.ktx)

  // Image loading
  implementation(libs.coil.compose)
  implementation(libs.coil.gif)

  // Media3 / ExoPlayer for in-memory streaming decrypted media
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)

  // CameraX Fallback for receipt capture (built-in camera fallback)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)

  // Coroutines
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // Performance / Baseline Profile installer
  implementation(libs.androidx.profileinstaller)

  // Haze Frosted Glass Blur
  implementation(libs.haze)
  implementation(libs.haze.materials)

  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}

abstract class VerifyReleaseApkNativeLibsTask : DefaultTask() {
  @get:InputFile
  abstract val apkFile: RegularFileProperty

  @TaskAction
  fun verify() {
    val apk = apkFile.get().asFile
    if (!apk.exists()) {
      throw GradleException("Release APK not found at ${apk.absolutePath}. Run assembleRelease first.")
    }

    val requiredAbis = listOf("arm64-v8a", "x86_64")
    val requiredLibs = listOf("libsqlcipher.so", "libmlkit_google_ocr_pipeline.so")

    val zip = ZipFile(apk)
    val entries = zip.entries().asSequence().map { it.name }.toSet()
    zip.close()

    val missing = mutableListOf<String>()
    for (abi in requiredAbis) {
      for (lib in requiredLibs) {
        val path = "lib/$abi/$lib"
        if (!entries.contains(path)) {
          missing.add(path)
        }
      }
    }

    if (missing.isNotEmpty()) {
      throw GradleException("Googlebook ABI verification failed! Missing required native libraries in release APK: $missing")
    }

    println("SUCCESS: Googlebook ABI verification passed! Verified libsqlcipher.so and ML Kit native libs exist for arm64-v8a and x86_64 in release APK.")
  }
}

tasks.register<VerifyReleaseApkNativeLibsTask>("verifyReleaseApkNativeLibs") {
  group = "verification"
  description = "Verifies that the release APK contains required native libraries for Googlebook ABIs (arm64-v8a and x86_64)"
  dependsOn("assembleRelease")
  apkFile.set(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
}
