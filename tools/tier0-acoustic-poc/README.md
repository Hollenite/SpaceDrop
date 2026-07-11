# Tier 0 Acoustic POC

Standalone tools to validate near-ultrasonic copresence on **your hardware** before any SpaceDrop app integration.

**Phase 1:** hardware validation (tone detect). **Phase 2:** FSK token encode/decode library.

**Out of scope:** `android/app/`, `windows/SpaceDrop.Tray/`, fusion, orbital UI.

## Contents

| Path | Role |
|------|------|
| [windows/AcousticEmitter/](windows/AcousticEmitter/) | PC sine-tone + FSK emitter (.NET 8 + NAudio) |
| [android/](android/) | Phone Goertzel + FSK decoder (standalone app) |
| [kotlin/fsk-core/](kotlin/fsk-core/) | JVM FSK library (encoder/decoder, tests) |
| [csharp/SpaceDrop.AcousticFsk/](csharp/SpaceDrop.AcousticFsk/) | C# FSK encoder (tray-side in Phase 3) |
| [shared/fsk-protocol.md](shared/fsk-protocol.md) | FSK wire format spec |
| [shared/goertzel-notes.md](shared/goertzel-notes.md) | Shared detection math |
| [fixtures/](fixtures/) | Golden WAV + `expected-tokens.json` |
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
| `fsk` | FSK token frame loop (requires `--device-id`) |

**Options**

```
--freq <Hz>       Target frequency (default 19000)
--mode <name>     continuous | burst | sweep | fsk
--device-id <uuid>  Required for fsk mode
--epoch <n>       FSK token epoch (default floor(unix/30))
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
dotnet run -- --mode fsk --device-id 550e8400-e29b-41d4-a716-446655440000 --epoch 100 --duration 120
```

### Phase 2 — CI tests (no mic)

**Kotlin** (from repo root):

```powershell
cd D:\PROJECTS\SpaceDrop\android
.\gradlew.bat -p ..\tools\tier0-acoustic-poc\android :fsk-core:test
```

**C#:**

```powershell
cd tools\tier0-acoustic-poc\csharp\SpaceDrop.AcousticFsk.Tests
dotnet test
```

**Regenerate golden WAV fixtures:**

```powershell
.\tools\tier0-acoustic-poc\fixtures\generate.ps1
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

### Measurement UI (Phase 1 gate helpers)

After **Start listening**, three sections help formal Steps A–D:

| UI section | Step | Usage |
|------------|------|-------|
| **Sweep helper** | A | During PC `--mode sweep`, tracks **peak SNR** and freq; **Reset peak** between steps |
| **Range test** | B | Manual **Hit** / **Miss** after each burst |
| **Burst auto test** | B | 3 s countdown → 60 s; hit = **SNR ≥ 18 dB for 500 ms** per burst onset (no clock sync) |
| **Silent room** | D | PC silent; **500 ms sustained** false DETECTED counts as FP |

| **FSK decode** | Phase 2 | PC `--mode fsk`; shows token hex, CRC, epoch match |

Trial marks and silent-test summaries are logged to logcat for pasting into [RESULTS.md](RESULTS.md).

PC burst mode prints `Burst N/M` (e.g. `Burst 3/20` for `--duration 60`) to sync with phone marks.

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

## Phase 2 exit gate

- **CI (automated):** Kotlin + C# tests pass; C# encode → Kotlin decode on golden fixtures **100%**; `bad-crc` rejected.
- **Manual:** Live FSK @ 2 m **≥ 90%** over 20 frames; 0 decodes when PC silent 60 s. See [RESULTS.md](RESULTS.md) Phase 2 section.
- **No main-app wiring** until Phase 2 manual gate passes.

## Audio notes

- PC is **emitter**, phone is **listener** (research-backed for SpaceDrop).
- 18.5–19.5 kHz is near the edge of adult hearing — brief whine possible on some hardware.
- Android uses `UNPROCESSED` mic when available to reduce AGC crushing high frequencies.
- Bottom-firing laptop speakers may reduce range; note in RESULTS.
