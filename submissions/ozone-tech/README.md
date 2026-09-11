# OffGrid Mesh

## Team
- Chirag (@chirag-exists)

## What it does
OffGrid Mesh is an offline, multi-hop mesh messaging application for Android devices. It turns nearby Android phones into a decentralized peer-to-peer network using Bluetooth Low Energy (BLE) and Wi-Fi Direct (via Google Nearby Connections API).

Key Features:
- **Zero Internet / Zero Server Dependency**: Works fully offline in emergency, disaster recovery, or low-connectivity scenarios.
- **Automatic Peer Discovery & Connection**: Devices continuously advertise and discover peers in the background using `P2P_CLUSTER`.
- **Multi-Hop Relay**: Messages automatically hop device-to-device (up to 3 hops by default) to reach users beyond direct radio range.
- **Loop Prevention & Deduplication**: Circular LRU message caching prevents network flood loops and duplicate messages.
- **Live Hop Tracking**: UI badges display whether a message was received `Direct`, or via `1 hop` / `2 hops`.

## Tech stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose, Material 3 (Dark Cyberpunk Theme)
- **Architecture**: MVVM with Android ViewModel & StateFlow
- **Connectivity**: Google Play Services Nearby Connections API (`P2P_CLUSTER` strategy)
- **Asynchronous Processing**: Kotlin Coroutines & Flow
- **Build System**: Gradle (Kotlin DSL)

## How to run

### Prerequisites
- Android Studio / Android SDK (API level 34+)
- 2 or more physical Android test devices running Android 8.0+ (API level 26+) with Bluetooth & Wi-Fi enabled. *(Emulators do not support physical BLE/Wi-Fi Direct hardware).*

### Step-by-step commands

1. **Navigate to team submission directory:**
   ```bash
   cd submissions/ozone-tech
   ```

2. **Build Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   *The output APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.*

3. **Install on physical Android devices via ADB:**
   ```bash
   adb -s <DEVICE_SERIAL> install app/build/outputs/apk/debug/app-debug.apk
   ```

4. **Launch & Setup:**
   - Open OffGrid Mesh on each device.
   - Enter display name and tap **Join Mesh**.
   - Grant **Nearby Devices / Bluetooth** and **Location** permissions when prompted.
   - Tap the **▶ Play button** in the top bar to start advertising and discovery.
   - Devices will connect automatically once in range.

## Screenshots / Demo
- Multi-hop mesh test: Device A connects to Device B, Device B connects to Device C. Device A sends message to Device C via Device B (2 hops!).
