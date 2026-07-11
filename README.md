# SpaceDrop

SpaceDrop is a nearby file-transfer system for Android and Windows. Share one or more files from your phone to a PC on the same network—without accounts, cloud relays, or typing IP addresses.

The phone discovers nearby PCs, ranks them by signal confidence, and sends files over a direct TCP connection. The Windows companion runs in the system tray: it advertises itself, accepts transfers, and saves files locally.

**Current version:** 0.1.0

---

## Downloads

Prebuilt binaries are published on [GitHub Releases](https://github.com/Hollenite/SpaceDrop/releases).

| Platform | Artifact | Notes |
|----------|----------|-------|
| Android | `SpaceDrop-<version>.apk` | Install via sideload (enable unknown sources / install from this source) |
| Windows | `SpaceDrop.Tray-<version>.zip` | Extract and run `SpaceDrop.Tray.exe` (x64, Windows 10 1809+) |

Keep the Android and Windows builds from the **same release** so discovery and transfer stay compatible.

---

## How it works

SpaceDrop separates **discovery** from **transfer**.

### Discovery (phone finds the PC)

The Windows tray advertises a stable device identity over three channels. The Android app merges them and scores each candidate:

| Channel | Role |
|---------|------|
| **Bluetooth LE** | Proximity and identity (GATT peripheral on the PC; central scan on the phone) |
| **mDNS / DNS-SD** | LAN reachability (`_spacedrop._tcp`) with host, port, and optional Wi-Fi BSSID |
| **Acoustic FSK** | In-room corroboration: the PC emits a near-ultrasonic (~19 kHz) token derived from its device ID; the phone listens only while discovery UI is open |

Acoustic evidence **never creates a device by itself**. It only boosts a PC already seen via BLE or mDNS, which prevents phantom “ghost” devices from audio alone.

### Transfer (phone → PC)

Once you pick a PC, the phone opens a single TCP connection to port **58392** and sends a batch of files:

```
PING { count: N }  →  PONG
  FILE_META → raw bytes → FILE_ACK   (file 1)
  FILE_META → raw bytes → FILE_ACK   (file 2)
  …
  close
```

Progress shows overall batch completion plus “file X of N”. Duplicate filenames on the PC are de-collided automatically.

Wire details: [`docs/protocol.md`](docs/protocol.md).

---

## Requirements

### Android

- Android 8.0 (API 26) or newer
- Wi-Fi (same LAN as the PC)
- Bluetooth (recommended; improves ranking and proximity)
- Microphone permission (for acoustic corroboration; optional but recommended)

### Windows

- Windows 10 version 1809 (build 17763) or newer, x64
- .NET 8 runtime (self-contained release builds do not require a separate SDK install)
- Bluetooth adapter optional — mDNS + TCP still work if BLE peripheral mode is unavailable
- Inbound firewall allowance for **TCP 58392**

---

## Quick start

### 1. Start the Windows companion

1. Download and extract the Windows release, or build from source (below).
2. Run `SpaceDrop.Tray.exe`.
3. Confirm the tray icon is present (check the overflow area near the clock).
4. Allow Windows Firewall access for TCP 58392 when prompted.

Received files are written to:

| Context | Path |
|---------|------|
| Running from this repository | `<repo>/received/` |
| Installed / standalone | `%USERPROFILE%\SpaceDrop\received\` |

Device identity is stored at `%LOCALAPPDATA%\SpaceDrop\device_id.txt`.

### 2. Install the Android app

1. Install the APK from the matching release.
2. Grant Bluetooth, nearby devices / Wi-Fi, and microphone permissions when asked.
3. Open SpaceDrop — nearby PCs appear on the home radar as they are discovered.

### 3. Pair, then send files

**Pair once**

1. On the PC: tray icon → **Pair phone (show QR)**.
2. On the phone: Home → show QR pairing (or open Pair for a new device) → scan the PC QR.
3. Accept the prompt on the PC. Both devices store each other’s identity fingerprint.

**Send**

1. Share one or more files to SpaceDrop (or use Transfer Lab after pairing).
2. Tap a **paired** PC. Transfers use mutual TLS; unpaired PCs are rejected.

The PC stays **silent** until the phone’s discovery UI arms a short ultrasonic confirmation window.

---

## Repository layout

```
SpaceDrop/
├── android/                 Android app (Jetpack Compose) + fsk-core module
├── windows/                 SpaceDrop.Tray (.NET 8 WinForms companion)
├── docs/                    Protocol and architecture notes
├── tools/tier0-acoustic-poc/  Acoustic validation POC, FSK libraries, fixtures
├── received/                Default receive folder when tray runs from the repo
└── spacedrop_pc.py          Legacy Python mDNS/TCP fallback (dev only)
```

| Path | Purpose |
|------|---------|
| [`android/app/`](android/app/) | Production Android client |
| [`windows/SpaceDrop.Tray/`](windows/SpaceDrop.Tray/) | Production Windows tray host |
| [`docs/protocol.md`](docs/protocol.md) | BLE, mDNS, acoustic, and TCP constants |
| [`tools/tier0-acoustic-poc/`](tools/tier0-acoustic-poc/) | Hardware validation and shared FSK encode/decode |
| [`windows/README.md`](windows/README.md) | Windows-specific build and troubleshooting |

Do not run `spacedrop_pc.py` and `SpaceDrop.Tray` at the same time — both bind TCP 58392.

---

## Build from source

### Android

**Prerequisites:** JDK 17, Android SDK (compile SDK 35), Android Studio or command-line tools.

```powershell
cd D:\PROJECTS\SpaceDrop\android
.\gradlew.bat assembleRelease
```

Debug build and unit tests:

```powershell
cd D:\PROJECTS\SpaceDrop\android
.\gradlew.bat testDebugUnitTest assembleDebug
```

APK output:

- Debug: `android/app/build/outputs/apk/debug/app-debug.apk`
- Release: `android/app/build/outputs/apk/release/app-release-unsigned.apk` (sign before distribution)

### Windows tray

**Prerequisites:** .NET 8 SDK; Visual Studio 2022 with the .NET desktop development workload is optional but convenient.

```powershell
cd D:\PROJECTS\SpaceDrop\windows\SpaceDrop.Tray
dotnet restore
dotnet build -c Release
```

Executable:

`windows/SpaceDrop.Tray/bin/x64/Release/net8.0-windows10.0.19041.0/SpaceDrop.Tray.exe`

Or open `windows/SpaceDrop.sln` in Visual Studio, select **Release | x64**, and build.

More detail: [`windows/README.md`](windows/README.md).

### Acoustic / FSK libraries (optional)

Shared encode/decode used by both apps:

```powershell
# Kotlin JVM tests
cd D:\PROJECTS\SpaceDrop\tools\tier0-acoustic-poc\kotlin
.\gradlew.bat :fsk-core:test

# C# tests
cd D:\PROJECTS\SpaceDrop\tools\tier0-acoustic-poc\csharp
dotnet test SpaceDrop.AcousticFsk.Tests\SpaceDrop.AcousticFsk.Tests.csproj
```

POC documentation: [`tools/tier0-acoustic-poc/README.md`](tools/tier0-acoustic-poc/README.md).

---

## Architecture (summary)

```
┌─────────────────────────────┐          ┌──────────────────────────────┐
│  Android                    │          │  Windows Tray                │
│                             │          │                              │
│  BLE scan ──┐               │          │  BLE GATT peripheral         │
│  mDNS ──────┼─► merge/score │◄─ LAN ───│  mDNS advertiser             │
│  mic FSK ───┘   rank        │          │  19 kHz FSK emitter          │
│                             │          │                              │
│  TCP client (multi-file) ───┼──────────│  TCP server :58392           │
│                             │          │  → received/                 │
└─────────────────────────────┘          └──────────────────────────────┘
```

Fusion confidence is capped at 1.0. Approximate weights (see protocol / scorer for exact constants):

| Signal | Contribution |
|--------|----------------|
| BLE RSSI tier | up to 0.35 |
| mDNS present | 0.15 |
| BLE + mDNS agreement | 0.05 |
| Wi-Fi BSSID match | 0.20 |
| Resolvable host | 0.10 |
| Acoustic token match | 0.15 |

Design notes: [`docs/architecture-review-2026-07-08.md`](docs/architecture-review-2026-07-08.md).

---

## Security model (current)

SpaceDrop uses **QR pairing** and **mutual TLS 1.3** for transfers (see [`docs/adr/0001-pairing-and-tls.md`](docs/adr/0001-pairing-and-tls.md)).

| Topic | Status |
|-------|--------|
| Transport | TLS 1.3 on port 58392 |
| Auth | ECDSA P-256 identity certificates; peer fingerprints stored after QR pair |
| Pairing | PC tray shows QR; phone scans; PC Accept/Decline |
| Shared token | Removed from the production path |
| Acoustic | Silent by default; phone arms a short emit window while discovery UI is open |
| Receive confirmation | Prompt when AlwaysAllow is off; paired peers default to auto-allow |

Discovery (BLE / mDNS / ultrasonic) is unauthenticated and never authorizes a transfer by itself. Do not expose port 58392 to the public internet.

---

## Permissions (Android)

| Permission | Why |
|------------|-----|
| Bluetooth / Nearby devices | Discover and identify the PC |
| Wi-Fi / network state | mDNS discovery and TCP transfer |
| Microphone | Decode the PC’s ultrasonic corroboration token |
| Internet | Local sockets (no cloud required) |

---

## Troubleshooting

| Symptom | What to check |
|---------|----------------|
| No PCs appear | Tray running? Same Wi-Fi? Firewall allowing TCP 58392? Bluetooth on (optional but helpful)? |
| PC appears then transfer fails | Confirm port 58392; disable VPN split-tunnel issues; verify the tray log shows a connection |
| Acoustic chip never lights | Mic permission granted; phone near laptop **speakers** (not headphones); tray log shows `Acoustic emit` |
| Files missing on PC | Check the receive path printed at tray startup (`received/` or `%USERPROFILE%\SpaceDrop\received\`) |
| Two apps fighting for the port | Stop `spacedrop_pc.py` if the tray is running |

---

## Current limitations

- Android is **send-only**; PC → phone transfer is not implemented.
- Room Discovery Lab handshake still uses the legacy plaintext probe and may not match the TLS server (use Share / Transfer Lab after pairing).
- Acoustic range depends on speaker and mic hardware.
- First-time pairing requires the PC and phone on the same LAN so the phone can reach the host advertised over BLE/mDNS.

---

## Contributing

1. Prefer small, reviewable changes against `main` (or the active feature branch).
2. Keep protocol changes documented in [`docs/protocol.md`](docs/protocol.md).
3. For acoustic work, follow the gate discipline in [`tools/tier0-acoustic-poc/PROGRESS.md`](tools/tier0-acoustic-poc/PROGRESS.md).
4. Run Android unit tests and the Windows tray build before opening a PR.

---

## License

License terms for this repository will be published with the first public release. Until then, all rights are reserved by the copyright holders.

---

## Links

- Repository: https://github.com/Hollenite/SpaceDrop
- Releases: https://github.com/Hollenite/SpaceDrop/releases
- Protocol: [`docs/protocol.md`](docs/protocol.md)
- Windows companion notes: [`windows/README.md`](windows/README.md)
- Acoustic POC: [`tools/tier0-acoustic-poc/README.md`](tools/tier0-acoustic-poc/README.md)
