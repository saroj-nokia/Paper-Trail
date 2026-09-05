# Changelog

All notable changes to Paper Trail will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1] - 2026-09-04

### Added
- **Core Ledger System**:
  - Receipt ingestion workflow supporting photo capture via system camera intent and existing gallery selection.
  - On-device optical character recognition (OCR) using ML Kit's bundled model to extract vendor names, transaction dates, and total amounts without network dependencies.
  - Warranty tracking with automated notification triggers via AndroidX WorkManager prior to expiration.
  - Recurring subscription tracker calculating aggregate monthly expenses and firing renewal alerts.
  - Spending analytics dashboard featuring interactive category breakdowns and warranty status summaries.
  - Local database encryption utilizing SQLCipher with 256-bit AES, storing database passphrases in Android Keystore-backed `EncryptedSharedPreferences`.
  - 4-step onboarding tutorial introducing core architecture and workflows, replayable on demand from Settings.
  - Global application biometric and device PIN lock screen.

- **SecureVault Isolated Subsystem**:
  - Cryptographically isolated file vault backed by a dedicated SQLCipher database and separate Keystore keys.
  - In-memory photo viewer with deep sub-sampling, pinch-to-zoom, pan gestures, and animated GIF decoding.
  - Video and audio player powered by AndroidX Media3 / ExoPlayer utilizing a custom decrypting `DataSource`, featuring edge-to-edge landscape playback.
  - In-memory PDF document viewer utilizing anonymous shared memory file descriptors (`android.os.SharedMemory` / `memfd_create`) to render pages without writing unencrypted files to disk.
  - Live cryptographic terminal monitor providing real-time logging of encryption operations, I/O rates, and cipher state transitions.
  - Configurable storage targets supporting sandboxed app-private storage, app-specific external storage, and user-selected directories via Storage Access Framework (SAF).

- **User Interface & Styling**:
  - Material 3 Expressive theming featuring dynamic color palettes and spring physics transitions.
  - Full predictive back gesture support for Android 14+ (API 34+).
  - Optional frosted-glass visual blur for the bottom navigation bar, top app bars, and crypto terminal.
  - High-contrast adaptive launcher icon featuring vector-drawn receipt perforation and security shield iconography with Material You monochrome support.

### Security
- **Two-Tier Envelope Encryption**: Individual files are encrypted with unique ephemeral AES-256-GCM data encryption keys (DEKs), wrapped by a master Key Encryption Key (KEK) held in the hardware-backed Android Keystore (StrongBox Keymaster where supported, with TEE fallback).
- **Per-Operation Biometric Authentication**: Decryption operations are strictly bound to individual `BiometricPrompt.CryptoObject` authentications rather than ambient session tokens.
- **Opaque Storage Layout**: Stored vault items are renamed to random UUID tokens with extensions and headers stripped to prevent filesystem inspection or mime-type profiling.
- **Master Recovery Credential**: PBKDF2-derived master password fallback with key-stretching and rate-limiting to maintain vault recoverability if hardware biometric keys are revoked.
- **Platform Integrity Verification**: SecureVault enforces pre-flight validation of SELinux `Enforcing` status and active device storage encryption before granting access.
- **Data Leak Prevention**: Explicitly disabled Android Auto Backup (`android:allowBackup="false"`) to prevent unencrypted cloud sync and device-to-device migration leaks.

### Changed
- **Chrome-Free Video Playback**: Bound a composition-local flag (`LocalForceHideBottomBar`) to the media player lifecycle, completely hiding the bottom navigation bar and surrounding chrome throughout video playback.
- **Version Metadata**: Updated application `versionCode` to 2 and `versionName` to 1.1 in `app/build.gradle.kts`.
- **Launcher Drawables**: Migrated adaptive launcher icons to pure vector drawables with an explicit 66dp safe-zone boundary, removing legacy raster assets.

### Fixed
- **Large-File Encryption OOM**: Resolved Out-Of-Memory exceptions during multi-gigabyte video encryption by migrating to a streaming chunked cryptographic pipeline.
- **Cold-Start Launch Latency**: Prewarm the SQLCipher database and Keystore passphrase initialization on a background thread (`Dispatchers.IO`) started early in `PaperTrailApp.onCreate()`, ensuring expensive cryptographic key setup is complete ahead of first UI access rather than blocking the main thread during `VaultViewModel` construction.
- **Navigation Backstack Mismatches**: Resolved an issue where the Dashboard's "View All" button navigated using a bare `navController.navigate(...)` call while the bottom bar used `popUpTo/saveState/launchSingleTop/restoreState`, creating a mismatched back stack that caused destinations to appear stuck. Standardized all top-level transitions using a shared `navigateToTopLevelDestination()` extension helper.
- **Dependency Alignment**: Aligned Jetpack Compose BOM, Media3, and Kotlin compiler plugin dependencies to resolve compiler warnings and bytecode version mismatches.
