# 📡 OffGrid Mesh

**Offline multi-hop mesh chat for Android — no internet, no cell towers, no servers.**

OffGrid Mesh turns nearby Android phones into a decentralized mesh network using Bluetooth Low Energy and Wi-Fi Direct. Messages hop device-to-device, reaching people far beyond your direct radio range.

---

## 🚀 How It Works

```
  📱 Alice  ←——BLE/WiFi——→  📱 Bob  ←——BLE/WiFi——→  📱 Charlie
     |                        |                         |
  Sends "Hello"          Receives &               Receives via
  (TTL=3)                auto-forwards             Bob (2 hops!)
                         (TTL=2)
```

1. **Each device advertises AND discovers** simultaneously using `P2P_CLUSTER` strategy
2. **Connections are automatic** — zero-click, zero-configuration handshakes
3. **Messages flood the mesh** — every node re-broadcasts to all peers (except the sender)
4. **Deduplication** prevents broadcast storms — a circular LRU cache of 500 message UUIDs
5. **TTL (Time-To-Live)** limits hops to 3 by default, preventing infinite loops

---

## 📋 Requirements

| Requirement | Details |
|---|---|
| **Devices** | 2+ Android phones (API 26+ / Android 8.0+) |
| **Google Play Services** | Required (for Nearby Connections API) |
| **Internet** | ❌ NOT required — fully offline |
| **Emulator** | ❌ NOT supported — needs real Bluetooth/Wi-Fi hardware |
| **Bluetooth** | ✅ Must be enabled |
| **Wi-Fi** | ✅ Must be enabled (for Wi-Fi Direct, not internet) |
| **Location** | ✅ Must be enabled (required for BLE scanning on Android) |

---

## 🛠️ Build & Install

### Prerequisites
- [Android SDK](https://developer.android.com/studio) installed
- ADB (Android Debug Bridge) on your PATH
- USB Debugging enabled on your test devices

### Build the APK

```bash
# Clone the repo
git clone https://github.com/chirag-exists/Ozone.git
cd Ozone

# Build debug APK
./gradlew assembleDebug
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Install on devices

```bash
# List connected devices
adb devices

# Install on each device (replace SERIAL with your device serial)
adb -s DEVICE_1_SERIAL install app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_2_SERIAL install app/build/outputs/apk/debug/app-debug.apk
```

Or simply transfer the APK file to each phone and install it manually.

---

## 📖 User Manual

### Step 1: First Launch — Set Your Name

When you open the app for the first time, you'll see a **Welcome dialog**:

1. Enter a display name (max 20 characters) — this is what other peers will see
2. Tap **"Join Mesh"**

Your name is saved locally and persists across app restarts.

### Step 2: Grant Permissions

A **yellow banner** will appear if permissions are missing:

1. Tap the **"Grant"** button
2. Accept **all** permission prompts:
   - **Location** — required for Bluetooth LE scanning (Android requirement, your location is never shared)
   - **Nearby Devices / Bluetooth** — required for mesh communication

> ⚠️ If you accidentally deny a permission, go to **Settings → Apps → OffGrid Mesh → Permissions** and enable them manually.

### Step 3: Start the Mesh

1. Tap the **▶ Play button** in the top-right corner of the screen
2. The status dot turns **green** and starts pulsing
3. The subtitle changes to **"Searching for peers…"**

Your device is now simultaneously:
- **Advertising** its presence to nearby devices
- **Discovering** other OffGrid Mesh devices in range

### Step 4: Connect with Peers

Connections happen **automatically** — no pairing, no codes, no buttons to press.

When another device running OffGrid Mesh starts their mesh:
1. Devices discover each other within seconds
2. A connection is established automatically
3. The **peer count badge** updates (e.g., "🟢 2")
4. The subtitle shows **"2 peers connected"**

> 📡 Effective range: ~30-100m depending on environment (Bluetooth LE + Wi-Fi Direct)

### Step 5: Send Messages

1. Type your message in the text field at the bottom
2. Tap the **green Send button** (or press Enter/Send on keyboard)
3. Your message appears instantly on the right side of the chat
4. It's broadcast to all connected peers

### Step 6: Read Messages

Each message bubble shows:

| Element | Description |
|---|---|
| **Sender name** | Shown in cyan above peer messages |
| **Message text** | The message body |
| **Hop badge** | `Direct` = received from sender directly. `1 hop`, `2 hops` = relayed through intermediary devices |
| **Timestamp** | When the message was originally sent (HH:MM format) |

**Your messages** appear on the right (green-tinted bubble).
**Peer messages** appear on the left (dark bubble with cyan sender name).

### Step 7: Stop the Mesh

1. Tap the **✕ button** in the top-right corner
2. The status dot turns gray
3. All peer connections are closed
4. You can restart anytime by tapping ▶ again

---

## 🔧 Understanding the Mesh

### Multi-Hop Routing

Messages don't just go to directly connected peers — they **hop** through the network:

```
You (TTL=3) → Peer A (TTL=2) → Peer B (TTL=1) → Peer C (dropped, TTL=0)
```

- Each device that receives a message **decrements the TTL** and **re-broadcasts** it
- Maximum of **3 hops** by default (covers a wide mesh area)
- The **hop badge** on each message tells you how far it traveled

### Deduplication

Without deduplication, a mesh network would create infinite loops:

```
A sends to B → B sends to C → C sends back to A → A sends to B → ...
```

OffGrid Mesh prevents this with a **circular LRU cache**:
- Every message has a unique UUID
- Each device remembers the last **500 message UUIDs**
- If a message arrives with a UUID we've already seen → **silently discarded**

### What "Direct" vs "Hops" Means

| Badge | Meaning |
|---|---|
| **Direct** | The sender is directly connected to you |
| **1 hop** | Message passed through 1 intermediary device |
| **2 hops** | Message passed through 2 intermediary devices |

---

## 🧪 Testing Guide

### Test 1: Two Devices (Basic Messaging)

1. Install on 2 phones
2. Start mesh on both
3. Wait for "1 peer connected" on both devices
4. Send messages back and forth
5. Verify messages show **"Direct"** hop badge

### Test 2: Three Devices (Multi-Hop)

1. Install on 3 phones (A, B, C)
2. Position them so:
   - A and B are in range of each other
   - B and C are in range of each other
   - A and C are **NOT** in direct range (e.g., different rooms)
3. Start mesh on all three
4. Send a message from A
5. Verify it arrives on C with **"2 hops"** badge

### Test 3: Deduplication

1. With 3+ connected devices, send messages rapidly
2. Verify each message appears **exactly once** on each device
3. No duplicate messages should ever appear

### Test 4: Reconnection

1. With 2 connected devices, stop mesh on one
2. Verify peer count drops on the other
3. Restart mesh — they should reconnect automatically

---

## 🏗️ Project Structure

```
app/src/main/java/com/example/offgridmesh/
├── data/
│   └── MeshPacket.kt          # Message data class (UUID, TTL, serialization)
├── mesh/
│   └── MeshManager.kt         # Nearby Connections wrapper (routing, dedup)
├── theme/
│   ├── Color.kt               # Dark cyberpunk color palette
│   └── Theme.kt               # Material3 theme configuration
├── ui/
│   ├── ChatScreen.kt          # Compose chat UI (bubbles, top bar, input)
│   └── ChatViewModel.kt       # State management bridge
└── MainActivity.kt            # Permission handling & entry point
```

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.
