# Changelog

All notable changes to Paper Trail will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1] - 2026-09-04 — Initial Public Release

Paper Trail's initial production release consolidates the complete development history, hardening cycle, and architectural security reviews into a single release milestone. The entries below document the engineering progression across the three major development phases.

---

### Phase 1: Core Ledger — Initial Build and Hardening

The foundational milestone establishing the offline-first personal financial ledger, on-device OCR pipeline, and encrypted Room/SQLCipher data store.

#### Added
- **Receipt Capture & OCR**: Implemented receipt image acquisition via system camera intents and device gallery selection. Integrated Google ML Kit's bundled on-device text recognition model (`play-services-mlkit-text-recognition`) to parse merchant name, purchase date, and grand total locally without network communication.
- **Warranty & Subscription Tracking**: Added expiration and renewal tracking with background reminder dispatch managed by AndroidX WorkManager.
- **Spending Analytics**: Implemented category breakdowns, monthly expenditure summaries, and warranty status health metrics on the main Dashboard.
- **Encrypted Local Storage**: Integrated SQLCipher 256-bit AES encryption for SQLite databases, with master database passphrases secured inside Android Keystore-backed `EncryptedSharedPreferences`.
- **Material 3 Interface**: Built the initial Jetpack Compose UI utilizing Material 3 design components, dynamic color theming, and responsive layout scaling.
- **Onboarding Tutorial**: Created a 4-page introductory walkthrough explaining the offline-first and zero-knowledge model, with a first-launch trigger and a replay entry in Settings that adds zero cold-start latency.
- **Licensing**: Licensed under the Apache License, Version 2.0.

#### Security & Robustness Fixes (Initial Hardening)
- **Runtime Notification Permission**: Added the missing runtime `POST_NOTIFICATIONS` permission request flow for Android 13+ (API 33+), which previously caused scheduled warranty and subscription reminders to fail silently.
- **OCR Exception Handling**: Fixed a fatal crash in `ReceiptOcrProcessor` where reading `.result` on an incomplete or failed ML Kit `Task` threw an unhandled `IllegalStateException`; updated to register completion listeners and fail gracefully to manual user entry.
- **Degraded-Security Visibility**: Identified that database and passphrase-store initialization failures were silently downgrading persistence to unencrypted storage without notifying the user. Added an explicit, visible degraded-security warning banner in the UI whenever encryption fails to initialize.
- **Release Shrinker Configuration (R8)**: Enabled R8 code and resource shrinking to reduce APK footprint. Added strict ProGuard keep rules for SQLCipher, Room, ML Kit, and AndroidX Biometric to prevent reflection-related crashes in minified release builds.
- **Permission & Dependency Pruning**: Removed unused CameraX libraries and eliminated the unneeded `android.permission.CAMERA` declaration, as image capture delegates directly to the platform's system camera application via standard intents.

#### Post-Device Test Fixes
- **SQLCipher Native Library Linkage**: Resolved an `UnsatisfiedLinkError` on `SQLiteConnection.nativeOpen` during physical device execution. The `net.zetetic:sqlcipher-android` dependency requires an explicit `System.loadLibrary("sqlcipher")` invocation before opening encrypted databases; added the native call along with an eager verification probe so the plaintext-fallback safety net activates reliably if native libraries fail to load.

#### Navigation Fixes
- **Top-Level Backstack Consistency**: Resolved an issue where the Dashboard's "View All" button navigated to the Ledger screen via a plain `navController.navigate(...)` call while the bottom navigation bar used a state-preserving `popUpTo/saveState/launchSingleTop/restoreState` pattern. The mismatch corrupted backstack state and caused the Dashboard button to become unresponsive after navigating through Ledger. Fixed by introducing a single shared `navigateToTopLevelDestination()` extension helper applied consistently across all top-level entry points.

#### Changed
- **Platform Target Modernization**: Raised `minSdk` from 24 to 31 (Android 12) and aligned `targetSdk` / `compileSdk` to 37 (Android 17), dropping unneeded legacy compatibility shims and targeting modern Android privacy primitives.

#### Performance Improvements
- **Background Database Prewarming**: Eliminated main-thread launch blocking caused by synchronous SQLCipher initialization and Keystore passphrase retrieval during `VaultViewModel` construction. Database prewarming and Keystore initialization now run on `Dispatchers.IO` immediately within `PaperTrailApp.onCreate()`.
- **Asynchronous Computation**: Offloaded ledger list filtering and dashboard statistical calculations from the main thread onto `Dispatchers.Default` using `.flowOn()`.
- **Formatter Churn Reduction**: Prevented high-frequency object allocation by wrapping `SimpleDateFormat` and `NumberFormat` instances in Compose `remember { }` blocks inside lazy item composables.
- **Bitmap Memory Management**: Fixed memory pressure during repeated receipt photo retakes by explicitly invoking `.recycle()` on replaced bitmaps and when exiting the capture screen.

---

### Phase 2: SecureVault — Isolated Encrypted File Vault

The architectural separation and implementation of SecureVault, a standalone encrypted storage and media viewer subsystem for sensitive files.

#### Added
- **Architectural Isolation**: Designed SecureVault as a fully isolated subsystem with its own independent SQLCipher database, separate Android Keystore keys (hardware StrongBox Keymaster backed with TEE fallback), and dedicated biometric authentication states that re-lock immediately upon navigating away or backgrounding the application.
- **Opaque Storage Camouflage**: Vault files are stripped of original filenames, extensions, and metadata, and written to disk as random UUID-named blobs. Raw files on disk are indistinguishable from high-entropy random data.
- **In-Memory Zero-Disk Viewers**:
  - **Photo Viewer**: Full-screen photo viewer featuring deep sub-sampling, pinch-to-zoom, pan gestures, and animated GIF decoding without writing unencrypted bitmaps to disk.
  - **Video & Audio Player**: Streaming media player powered by AndroidX Media3 / ExoPlayer utilizing a custom decrypting `DataSource`. Supports chunked, independently authenticated decryption for real-time seeking and full-screen landscape playback.
  - **In-Memory PDF Viewer**: Custom PDF page renderer utilizing anonymous, RAM-only file descriptors created via `android.system.Os.memfd_create` (replacing an earlier prototype that used fragile reflection into private `android.os.SharedMemory` fields), guaranteeing unencrypted document pages never touch physical storage.
- **Platform Integrity Gate**: Implemented pre-flight checks verifying that SELinux is in `Enforcing` mode and device storage encryption is active prior to unlocking SecureVault. The integrity check is explicitly scoped to SecureVault, ensuring custom ROMs or developer environments can still access the core ledger.
- **Live Crypto Terminal**: Built an embedded, real-time diagnostic terminal streaming active cryptographic operations, block throughput, and cipher transitions in memory (keys, IVs, and plaintext are never logged to memory, disk, or logcat).

#### Security Audits & Hardening
- **Master Recovery Credential Overhaul**: Remediated three critical vulnerabilities in the fallback recovery passphrase mechanism (used when device biometric enrollment changes invalidate hardware keys):
  - Replaced unsalted SHA-256 with PBKDF2 (210,000 iterations) using a cryptographic salt.
  - Migrated credential storage from plaintext `SharedPreferences` to `EncryptedSharedPreferences`.
  - Removed an insecure fallback bypass that silently auto-accepted and saved any input of 4+ characters if no master credential had been explicitly configured.
  - Implemented exponential-backoff rate limiting on failed unlock attempts.
- **Shadow Metadata Encryption**: Discovered that an offline metadata fallback snapshot (designed to protect against destructive Room migrations) was writing filenames, MIME types, and wrapped keys to a plaintext JSON file. Re-architected the safety net to encrypt snapshots using `EncryptedFile` with a dedicated Android Keystore key.
- **Cloud Backup Exclusion**: Explicitly set `android:allowBackup="false"` globally in the manifest, closing potential data exposure risks where sensitive preference files could be synced to unencrypted cloud backups or device-to-device transfers.
- **Storage Access Framework (SAF) Re-implementation**: Fixed a completely broken "persistent custom folder" storage option that ignored user-selected directories and attempted invalid hardcoded paths blocked by Scoped Storage. Rebuilt using proper `DocumentFile` APIs and persisted URI permission grants.

#### Cryptographic & Media Streaming Fixes
- **Chunked Streaming Cryptography (OOM Fix)**: Resolved fatal `OutOfMemoryError` crashes when importing multi-gigabyte video files by abandoning single-buffer `cipher.doFinal()` calls in favor of a chunked streaming pipeline using `CipherInputStream` and `CipherOutputStream` with fixed-size buffers.
- **Envelope Encryption Throughput Optimization**: Resolved severe throughput bottlenecks caused by routing bulk file encryption directly through the hardware StrongBox key. Implemented two-tier envelope encryption: files are encrypted via unique software AES-256-GCM data encryption keys (DEKs) running at line speed with hardware CPU AES acceleration, while the StrongBox/TEE key acts solely as a Key Encryption Key (KEK) wrapping the 32-byte DEK.
- **Chrome-Free Video Playback**: Fixed an issue where the app's bottom navigation bar remained visible over playing video (including in full-screen mode) because the player was hosted as a conditional overlay inside SecureVault rather than an independent navigation destination. Resolved by introducing `LocalForceHideBottomBar`, allowing the player to programmatically collapse and hide the bottom bar during its entire active lifecycle.

---

### Phase 3: UI Polish, Motion, and Platform Currency

The visual refinement, animation orchestration, and platform compliance phase.

#### Added
- **Material 3 Expressive Motion**: Standardized spring-based motion tokens across the application (`PaperTrailMotion`), replacing stacked and redundant animation modifiers that caused card expand/collapse gestures to feel sluggish.
- **Predictive Back Navigation**: Implemented Android 14+ (API 34+) predictive back gesture handling across navigation destinations and modal bottom sheets.
- **Optional Frosted Glass**: Integrated an optional background blur effect powered by the Haze library (disabled by default in Settings). Tuned to minimal intensity on top and bottom app bars, and utilized at full intensity for internal scrolling log blur inside the SecureVault Crypto Terminal.

#### Changed
- **Adaptive Vector Iconography**: Replaced a lossy raster JPG launcher asset with a genuine vector drawable featuring receipt perforation and security shield motifs, including a dedicated monochrome silhouette layer for Android 13+ Material You themed icons.
- **Release Versioning**: Formally bumped `versionCode` to 2 and `versionName` to 1.1 in `app/build.gradle.kts`.

#### Fixed
- **Compose BOM Dependency Alignment (Release Crash)**: Resolved a release-only `NoSuchMethodError` on `FlowRow` caused by the Compose BOM being pinned to an outdated version (`2024.09.00`) while other transitive libraries pulled newer Compose artifacts. Upgraded the BOM to `2026.08.00`, cleaned up deprecated API usages (migrating to auto-mirrored directional icons and modern scrollable tab rows), and aligned compiler plugin compatibility.

#### Verified
- **Complete Zero-GMS Audit**: Audited all project dependencies and source files to verify zero runtime reliance on Google Play Services, Firebase, or Play Integrity APIs. Confirmed that ML Kit Text Recognition functions exclusively via bundled on-device model artifacts, certifying complete functionality on de-Googled custom Android ROMs (such as GrapheneOS, CalyxOS, and LineageOS).
