# Paper Trail

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2012%2B%20(API%2031%2B)-green.svg)](https://developer.android.com)
[![Offline First](https://img.shields.io/badge/Network-100%25%20Offline-brightgreen.svg)]()
[![Privacy](https://img.shields.io/badge/Telemetry-Zero-red.svg)]()

**Paper Trail** is an open-source, offline-first personal ledger and encrypted document vault for Android. Built specifically for privacy-conscious users, de-Googled devices, and the custom-ROM / FOSS Android community (GrapheneOS, CalyxOS, LineageOS), Paper Trail tracks physical receipts, warranty expirations, and recurring subscriptions while providing a cryptographically isolated **SecureVault** for sensitive files.

---

## Philosophy & Privacy Guarantees

- **100% Offline by Design**: Paper Trail declares zero network dependencies and zero internet communication. No cloud sync, no server infrastructure, and no remote endpoints.
- **Zero Telemetry / Zero Trackers**: No analytics SDKs, no advertising frameworks, and no third-party crash reporters (e.g., Firebase Crashlytics or Sentry).
- **Google Play Services Independent**: Optical Character Recognition (OCR) utilizes a self-contained, bundled on-device model from ML Kit. It does not invoke Google Play Services or require microG to process documents.
- **Hardware-Enforced Cryptography**: All persistent data resides inside SQLCipher databases encrypted with keys managed by the hardware-backed Android Keystore (StrongBox Keymaster where available, falling back to TEE).
- **No Cloud Backup Leakage**: Android's OS-level Auto Backup and Device-to-Device transfer are explicitly disabled (`android:allowBackup="false"`).
- **Zero-Knowledge Architecture**: If the application is removed or device cryptographic material is purged without a backup, data cannot be recovered by anyone—including the authors. There are no administrative backdoors or master keys.

---

## Features

### 1. Core Ledger (Receipts, Warranties & Subscriptions)
- **On-Device Receipt Scanning**: Capture documents via the system camera (invoked via Storage Access Framework / system intents without bundled camera bloat) or import existing images from storage.
- **Local OCR Extraction**: Automatically extracts merchant name, purchase date, and grand total on-device using bundled ML Kit Text Recognition without leaking bitmap data.
- **Warranty Health Tracker**: Tracks purchase dates and warranty duration with background notifications via Android WorkManager prior to expiration.
- **Subscription Monitor**: Tracks billing intervals, renewal dates, and automatically calculates aggregate monthly/annualized commitments.
- **Financial Analytics**: High-performance, client-side spend breakdown visualizer organized by category and payment method.
- **Encrypted Local Database**: The ledger database is encrypted with SQLCipher via 256-bit AES. Database passphrases are stored in `EncryptedSharedPreferences` backed by the Android Keystore.
- **Interactive Onboarding**: A 4-page introductory guide explaining the architecture, replayable at any time from Settings.
- **Global App Lock**: Biometric (fingerprint/face) or device PIN lock gates the entire application.

### 2. SecureVault (Isolated Encrypted File Vault)
SecureVault is an architecturally separate subsystem designed to store, manage, and inspect arbitrary sensitive files with zero information leakage:

- **Isolated Cryptographic Domain**: SecureVault operates on an independent SQLCipher database with separate Keystore keys and an independent authentication lifecycle. Entering SecureVault requires fresh authentication, and the vault locks immediately upon navigating away or backgrounding the application.
- **Opaque Storage Blobs**: Files written to disk are stripped of original metadata, filenames, and file extensions. They are stored as pseudo-random UUID-named binaries indistinguishable from high-entropy noise.
- **Two-Tier Envelope Encryption**:
  - Each stored file is encrypted with a unique, randomly generated 256-bit AES-GCM Data Encryption Key (DEK).
  - The DEK is encrypted (wrapped) by a hardware-backed Key Encryption Key (KEK) residing in the Android Keystore (`KeyGenParameterSpec` with StrongBox backing on supported hardware).
  - Bulk cryptographic operations run at line speed with hardware AES acceleration, while the Keystore security boundary handles only 32-byte key wrapping.
- **Per-Operation Biometric Binding**: Decryption operations require a fresh cryptographic authorization token generated via `BiometricPrompt.CryptoObject`, preventing session-hijacking or unauthorized ambient access.
- **Streaming Chunked Encryption**: Large media files (video/audio) are segmented into authenticated chunks. Playback is streamed dynamically, eliminating RAM exhaustion (OOM) and avoiding decrypted intermediate writes to disk.
- **Zero-Disk In-Memory Viewers**:
  - **Photos & GIFs**: Full-screen photo viewer featuring sub-sampling, pinch-to-zoom, pan, and animated GIF decoding entirely in memory.
  - **Video & Audio Player**: Powered by Media3 / ExoPlayer utilizing a custom decrypting `DataSource`. Includes fullscreen landscape mode and complete removal of UI chrome during playback.
  - **PDF Reader**: Renders PDF pages directly from memory via anonymous shared memory file descriptors (`android.os.SharedMemory` / `memfd_create`), guaranteeing that unencrypted PDF bytes never touch flash storage.
- **Flexible Storage Targets**: Store encrypted payloads in private internal app sandboxes, app-specific external storage, or a user-defined external directory via the Storage Access Framework (SAF).
- **Device Integrity Gate**: Verifies that SELinux is in `Enforcing` mode and file-based encryption (FBE) is active prior to unlocking the vault.
- **PBKDF2 Recovery Passphrase**: User-configured master credential (salted and stretched via PBKDF2) to recover access if biometric hardware enrollments change and invalidate biometric Keystore keys.
- **Live Crypto Terminal**: An embedded cryptographic monitor displaying real-time stream status, key exchanges, and I/O metrics for complete operational transparency.

### 3. Modern Material 3 UI
- Built with Jetpack Compose following Material 3 guidelines.
- Dynamic color support and fluid spring animations throughout navigation transitions.
- Native support for Android 14+ predictive back gestures.
- Optional frosted-glass background blur effects across toolbars, navigation bars, and the cryptographic terminal (disabled by default; zero overhead when inactive).

---

## How to Use

### Scanning a Receipt
1. Open the application and authenticate with your biometric credential or PIN.
2. Tap the floating action button (**+**) on the Dashboard or Ledger screen.
3. Choose **Camera** to launch the system camera or **Gallery** to pick an existing image.
4. The on-device OCR engine will parse the document text locally and pre-populate:
   - Merchant Name
   - Transaction Date
   - Total Amount
   - Item Category
5. Set warranty terms or mark the entry as a recurring subscription if desired.
6. Tap **Save Entry**. The image and receipt record are encrypted and committed to the local SQLCipher database.

### Setting Up SecureVault
1. Select the **Vault** tab on the navigation bar.
2. Complete the initial setup by defining a master recovery passphrase (used as fallback if device biometric profiles change).
3. Confirm biometric enrollment. SecureVault checks platform integrity (SELinux enforcement and block encryption).
4. Tap **Add Files** to import images, videos, audio clips, or PDF documents.
5. Selected files are immediately encrypted with unique AES-GCM data keys and renamed to opaque identifiers.
6. Tap any file to view it inside SecureVault's dedicated in-memory viewer.
7. To manually lock the vault, tap the lock icon in the top app bar, or simply background the application.

---

## Architecture & Tech Stack

```
com.example
├── securevault/               # Cryptographically isolated vault module
│   ├── crypto/                # Envelope encryption, Keystore & StrongBox managers
│   ├── data/                  # SecureVault SQLCipher database & DAOs
│   ├── media/                 # Custom ExoPlayer decrypting data source & stream decoders
│   ├── model/                 # Secure file items & cryptographic state models
│   └── ui/                    # SecureVault UI, Crypto Terminal, In-Memory Viewers
├── ui/
│   ├── components/            # Reusable M3 Compose components & receipt styling
│   ├── navigation/            # Navigation Compose graph & routes
│   ├── screens/
│   │   ├── auth/              # Biometric & PIN gate screens
│   │   ├── dashboard/         # Aggregated metrics, charts, quick actions
│   │   ├── detail/            # Receipt/warranty detail & edit screens
│   │   ├── settings/          # App settings, theme & storage preferences
│   │   ├── tutorial/          # Onboarding walkthrough
│   │   └── vault/             # Ledger item listings & filtering
│   └── theme/                 # M3 ColorScheme, Typography, Motion, FrostedGlass
└── viewmodel/                 # MVVM StateFlow & coroutine view models
```

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.0+ (100% Kotlin) |
| **UI Framework** | Jetpack Compose (Material 3 Expressive) |
| **Architecture** | MVVM with Coroutines & StateFlow |
| **Local Persistence** | Room + SQLCipher (256-bit AES) |
| **Key Management** | Android Keystore (StrongBox Keymaster / TEE) |
| **Text Recognition** | ML Kit Text Recognition (`com.google.mlkit:text-recognition` bundled model) |
| **Media Playback** | AndroidX Media3 / ExoPlayer with custom streaming `DataSource` |
| **Background Jobs** | AndroidX WorkManager (local reminder dispatch) |
| **Minimum SDK** | Android 12 (API level 31) |
| **Target/Compile SDK** | Android 17 (API level 37) |

---

## Building from Source

### Prerequisites
- Java Development Kit (JDK) 17 or higher
- Android SDK with API level 37 and build tools installed

### Build Steps

1. **Clone the repository:**
   ```bash
   git clone https://github.com/example/paper-trail.git
   cd paper-trail
   ```

2. **Assemble Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   The compiled APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

3. **Run Local Unit Tests:**
   ```bash
   ./gradlew :app:testDebugUnitTest
   ```

4. **Assemble Release APK:**
   ```bash
   ./gradlew assembleRelease
   ```

---

## Security Model & Disclosures

- **Loss of Biometric Profile**: Because keys in the Android Keystore can be permanently invalidated when fingerprints are added or removed, users must maintain their master recovery passphrase to retain access to SecureVault.
- **Physical Device Compromise**: While hardware-backed keys prevent extraction of raw cryptographic material, an adversary with physical access to an unlocked device in an active biometric session may access unlocked state. Always enable the app-level biometric lock.
- **Reporting Vulnerabilities**: To report security issues or implementation flaws, please file a confidential report via repository security advisories or open a private issue.

---

## License

Paper Trail is licensed under the [Apache License, Version 2.0](LICENSE).
You may obtain a copy of the License at:

```
http://www.apache.org/licenses/LICENSE-2.0
```
