# Tier 0 Phase 1 — Acoustic validation POC

Standalone tools to validate near-ultrasonic copresence on **your hardware** before any SpaceDrop app integration.

**Out of scope:** `android/app/`, `windows/SpaceDrop.Tray/`, fusion, orbital UI.

## Contents

| Path | Role |
|------|------|
| [windows/AcousticEmitter/](windows/AcousticEmitter/) | PC sine-tone emitter (.NET 8 + NAudio) |
| [android/](android/) | Phone Goertzel detector (standalone app) |
| [shared/goertzel-notes.md](shared/goertzel-notes.md) | Shared detection math |
| [RESULTS.md](RESULTS.md) | Measurement log + exit gate |

## Quick start

### Windows emitter

```powershell
cd tools\tier0-acoustic-poc\windows\AcousticEmitter
dotnet run -- --freq 19000 --mode continuous --duration 30
```

**Modes**

| Mode | Behavior |
|------|----------|
| `continuous` | Steady sine at `--freq` |
| `burst` | 1 s on, 2 s off (repeat until duration) |
| `sweep` | 17–21 kHz in 500 Hz steps, 5 s per step |

**Options**

```
--freq <Hz>       Target frequency (default 19000)
--mode <name>     continuous | burst | sweep
--duration <sec>  Run time (default 60; 0 = until Ctrl+C)
--amplitude <0-1> Peak gain (default 0.4)
--sample-rate     44100 or 48000 (default 44100)
--wav <path>      Optional: save synthesized WAV reference
--list-devices    List playback devices (skips headphones by default)
--device <index>  Force output device index
--device-name     Match device name substring (e.g. "Speakers")
--windows-default Use Windows default device (may be earphones)
```

Examples:

```powershell
dotnet run -- --mode sweep --duration 120
dotnet run -- --freq 18500 --mode burst --duration 60
dotnet run -- --freq 19000 --mode continuous --wav tone-19k.wav --duration 10
```

### Android detector

**Prerequisites:** Android Studio JBR (JDK 21) and Android SDK. If Gradle fails with `25.0.1`, your system `java` is too new — the project's `gradle.properties` pins JDK 21 via `org.gradle.java.home`.

Build from repo root (uses main Gradle wrapper):

```powershell
cd D:\PROJECTS\SpaceDrop\android
.\gradlew.bat -p ..\tools\tier0-acoustic-poc\android assembleDebug
```

Install (use full path to `adb` if not on PATH):

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r ..\tools\tier0-acoustic-poc\android\app\build\outputs\apk\debug\app-debug.apk
```

Or open `tools/tier0-acoustic-poc/android` in Android Studio and Run.

1. Grant **microphone** permission when prompted.
2. Tap **Start listening**.
3. Set target frequency to match PC emitter (slider 17–21 kHz).
4. Watch magnitude bar and **DETECTED** indicator.

Logcat:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -s SpaceDropAcousticPoc
```

## Measurement protocol

Run on primary pair (e.g. Xiaomi + Acer), then optional second phone.

| Step | PC | Phone | Pass |
|------|----|-------|------|
| **A** Rolloff | `--mode sweep` | Record max magnitude per freq in RESULTS | Peak band identified |
| **B** Range | `--mode burst` at best freq | Walk 0.5–5 m, 20 trials at 2 m | ≥ 90% at 2 m; detect at ≥ 3 m |
| **C** Placement | Best freq | Rotate phone, lid open/closed | Document sensitivity |
| **D** False positives | **Silent** | Listen 60 s | 0 false DETECTED |
| **E** Wall | Same vs adjacent room | Qualitative SNR drop | Detection weaker through wall |
| **F** Noise | Burst + speech/TV | Same room | Note if still detects |

Record everything in [RESULTS.md](RESULTS.md). Gate decision steps: [GATE_DECISION.md](GATE_DECISION.md).

## Phase 1 exit gate

- Usable tone band on PC (typically 18–20 kHz).
- ≥ **90%** burst detection at **2 m**, quiet room, 20 trials.
- **0** false positives in 60 s silent test.

If gate **fails** (< 50% at 2 m or no energy above 18 kHz): do **not** start Phase 2; try external USB speaker or lower band and document in RESULTS.md.

## Audio notes

- PC is **emitter**, phone is **listener** (research-backed for SpaceDrop).
- 18.5–19.5 kHz is near the edge of adult hearing — brief whine possible on some hardware.
- Android uses `UNPROCESSED` mic when available to reduce AGC crushing high frequencies.
- Bottom-firing laptop speakers may reduce range; note in RESULTS.
