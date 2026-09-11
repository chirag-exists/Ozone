**Team Name:** Ozone Tech
**Team Members:** @chirag-exists
**Project Title:** OffGrid Mesh - Offline Multi-Hop Mesh Chat for Android

### Summary
We built **OffGrid Mesh**, a decentralized, zero-internet offline messaging Android application. It utilizes Bluetooth Low Energy (BLE) and Wi-Fi Direct via Google Nearby Connections (`P2P_CLUSTER` strategy) to form a multi-hop mesh network. Messages automatically hop device-to-device (up to 3 hops) to reach users beyond direct radio range, with built-in TTL decrementing, LRU deduplication, and real-time hop badges on Jetpack Compose UI.

### How to run
1. Navigate to `submissions/ozone-tech/`
2. Run `./gradlew assembleDebug`
3. Install the APK onto 2+ physical Android devices via `adb install app/build/outputs/apk/debug/app-debug.apk`
4. Grant permissions and tap ▶ to connect nearby devices.

For full detailed documentation, see [submissions/ozone-tech/README.md](submissions/ozone-tech/README.md).

### Demo
Multi-hop routing tested across physical Android devices: Device A <-> Device B <-> Device C (Messages relayed across 2 hops seamlessly without cell towers or internet).
