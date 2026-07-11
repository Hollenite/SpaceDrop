# Tier 0 — Progress tracker

**Last updated:** 2026-07-08  
**STATUS:** Phase 2 **CI GATE PASS**; Step 1 (restart bug + decode reliability) **LIVE CONFIRMED**. Steps 2–6 code-complete + build-verified (Android `testDebugUnitTest`+`assembleDebug` green; Windows tray build green): Step 2 arch review doc; Step 3 acoustic folded into main app fusion + tray emitter (Phase 3/4 code DONE, **live corroboration gate pending**); Step 4 frontend placeholders wired; Step 5 Home flicker fix + hybrid send UI; Step 6 multi-file transfer over one connection (**live 2+ file gate pending**). Phase 1 **GATE PASS**. GitHub [#3](https://github.com/Hollenite/SpaceDrop/issues/3).

> Rules and conventions: [`.agents/skills/spacedrop-tier0/SKILL.md`](../../.agents/skills/spacedrop-tier0/SKILL.md)

---

## Phase checklist

Status values only: **DONE** | **IN PROGRESS** | **NOT STARTED**

### Phase 1 — Hardware validation POC

| Step | Exit criterion | Status |
|------|----------------|--------|
| POC scaffold | `tools/tier0-acoustic-poc/` with README, RESULTS template, Goertzel notes | **DONE** |
| Windows emitter | .NET 8 NAudio sine emitter (continuous / burst / sweep) | **DONE** |
| Android detector | Standalone app: AudioRecord + Goertzel + DETECTED UI | **DONE** |
| Gradle JDK pin | `org.gradle.java.home` → JDK 21 in `android/gradle.properties` | **DONE** |
| Speaker auto-select | `AudioOutputSelector` skips earphones; prefers Realtek speakers | **DONE** |
| Informal desk test | SNR 50–60 dB @ 19 kHz burst ON; ~11 dB OFF; Xiaomi `UNPROCESSED` mic | **DONE** |
| POC measurement UI | Range trial, 60 s FP test, peak SNR, burst auto test | **DONE** |
| Emitter burst index + gain gate | `Burst N/M` logs; 1 s ON via Gain 0/0.4 (no Stop/Play) | **DONE** |
| A. Speaker rolloff | Sweep 17–21 kHz; peak band ≥ 18 kHz in RESULTS | **DONE** |
| B. Range matrix | ≥ 90% @ 2 m (gate); 0.5–2 m documented | **DONE** |
| C. Placement | Phone angle, lid, elevation notes | **NOT STARTED** (optional; facing-PC only in RESULTS) |
| D. False positives | 60 s silent PC → 0 false DETECTED | **DONE** |
| E. Wall test | Same vs adjacent room SNR | **NOT STARTED** (optional) |
| F. Ambient noise | Speech / TV / cooker notes | **IN PROGRESS** (speech + cooker observed; TV not run) |
| RESULTS.md complete | Gate-required tables filled | **DONE** |
| Phase 1 gate declared | Overall **PASS** in RESULTS.md | **DONE** |

### Phase 2 — FSK token encode/decode library

| Step | Exit criterion | Status |
|------|----------------|--------|
| FSK protocol spec | Preamble + 20-bit token + 4-bit CRC; 18.5–19.5 kHz; ≥ 80 ms symbols | **DONE** |
| Kotlin encoder/decoder | Shared module with Goertzel two-bin decode | **DONE** |
| C# encoder/decoder | Tray-side encode matching Kotlin | **DONE** |
| WAV fixture unit tests | Recorded fixtures; decode accuracy gate in CI | **DONE** |
| POC FSK integration | Emitter `--mode fsk`; Android FSK UI; noise-floor reset | **DONE** |
| Manual live @ 2 m | ≥ 90% frame decode over 20 transmissions | **NOT STARTED** |
| Phase 2 gate declared | CI fixture PASS; manual live pending | **IN PROGRESS** |

### Phase 3 — App wiring

| Step | Exit criterion | Status |
|------|----------------|--------|
| PC AcousticBeaconService | Started from tray host alongside mDNS/BLE | **DONE** (`AcousticEmitterService` in `SpaceDrop.Tray`, epoch-rollover loop) |
| Android AcousticCopresenceListener | Home/Share visibility lifecycle; no always-on background | **DONE** (`core/acoustic/AcousticListener`, started/stopped with periodic refresh) |
| RECORD_AUDIO permission | Main manifest + runtime flow | **DONE** (`AcousticPermissions`; requested on Home/picker/lab) |
| Lifecycle stability | No leaked scopes across Home ↔ Share | **DONE** (per-session `AudioRecord` + `cancelAndJoin`, own capture job) |
| Phase 3 gate declared | Manual stability sign-off in PROGRESS | **IN PROGRESS** (code + build done; live PC+phone gate pending) |

### Phase 4 — Fusion integration

| Step | Exit criterion | Status |
|------|----------------|--------|
| Merger extension | `hasAcoustic`, `acousticTokenMatch` on candidate | **DONE** (`acousticTokenMatch` on `MergedDiscoveryCandidate`) |
| FusionBreakdown extension | Acoustic weight fields | **DONE** (`acousticWeight`, `acousticMatch`) |
| FullFusionScorer rebalance | Acoustic weight; cap 1.0; token must match `device_id` hash | **DONE** (`WEIGHT_ACOUSTIC=0.15`; corroboration-only; `FullFusionScorerTest` green) |
| BLE Discovery Lab chip | Acoustic AssistChip + log line | **DONE** (Acoustic chip + `acoustic=`/`wAcoustic=` log) |
| docs/protocol.md Tier 0 | Freq band, token rotation, no payload over audio | **DONE** (see architecture review; token = `SHA-256(deviceId‖epoch)`→20 bits, 30 s epoch) |
| Ghost-PC invariant | Acoustic alone never creates a device row | **DONE** (weight gated on `hasBle||hasMdns`; test `acousticMatch_neverAppliesWithoutBleOrMdns`) |
| Phase 4 gate declared | All above verified | **IN PROGRESS** (code + unit tests done; live corroboration gate pending) |

---

## Discovered but not yet planned

| Finding | Notes |
|---------|-------|
| Earphone routing bug | Fixed via `AudioOutputSelector`. |
| Burst 1 s ON / 2 s OFF | POC test duty cycle only. |
| Bottom-firing Acer speakers | Adequate to **2 m** in home room; 3/5 m not tested. |
| Auto-DETECTED vs manual marks | Step B gate uses manual and/or burst auto (500 ms SNR hold). |
| WasapiOut Stop/Play | Truncated audible tone; fixed with **gain gating**. |
| Burst auto cooldown | SNR 13–19 dB during OFF blocked re-arm; fixed with **2.2 s wall-clock cooldown**. |
| Stuck high SNR (POC) | Intermittent after Start listening; noise-floor adaptation may stall. |
| Speech / pressure cooker | Single-tone false triggers; Phase 2 FSK+CRC required. |
| FSK no-decode after capture restart | New capture session (new PID) decoded 0 tokens despite `detected=true`. Cause: `PreambleDetector.noiseFloor` seeds at 1e-6 and only tracks downward, so a decoder created mid-transmission never sees leading silence → gap SNR stays >8 dB → `gapArmed` never armed. Fixed with absolute `GAP_QUIET_MAGNITUDE=5e-5` floor + per-session `AudioRecord` ownership. JVM repro added. **LIVE CONFIRMED 2026-07-08** — restarted session (PID 27134, mid-transmission) decoded `0x5142E match=true` 14× continuously; pre-fix went silent. |
| FSK low live decode reliability | Same 2026-07-08 restart session: 14 valid decodes vs **66 CRC rejects (~18% success)**. Rejects = recurring `0x79E3F` (systematic ~1-block symbol misalignment after preamble lock) + 1-2 bit near-misses (`0x5142F`, `0x5102E`, `0x5042E`, `0x0142E`, `0x7142E`). **FIXED (JVM-confirmed 2026-07-08)** with sub-block preamble->data transition detection (aligns symbol clock to true onset, not block grid) + centered-core symbol vote + ratio-based gap-gate disarm. Deterministic phase-sweep test now decodes 16/16 sub-block offsets (was 6/16); full suite green. **LIVE CONFIRMED 2026-07-08** (PID 20880): ~57 valid decodes vs ~10 rejects (was 14:66); once phone moved close (~frame 40), an unbroken ~50-decode streak (one per loop frame, 2.5 min, zero rejects). Only 1 `0x79E3F` in the whole log. Remaining rejects are 1-2 bit near-misses at marginal SNR (edge of range), not misalignment. |
| Windows tray receive-side accept/decline UI | Tray still **auto-accepts** every incoming transfer (no per-transfer confirmation prompt). Out of scope for Steps 2–6 (noted in plan §Global out of scope). Needed before real-world use so a PC user can decline unexpected files. |
| Multi-file duplicate filenames | Batches can contain files with identical names; receiver now de-collides by appending ` (n)` before the extension (`UniqueDestination`). |
| Multi-file cancel granularity | Cancelling a batch mid-flight aborts the whole connection; partially-written current file remains on the PC. Acceptable for prototype; a clean per-file rollback would need a tmp-then-rename on the receiver. |

---

## Decisions log (append-only)

| Date | Decision |
|------|----------|
| 2026-07-03 | Phase 1 delivered as **standalone POC** (commit `dea16a3`). |
| 2026-07-03 | **PC emitter, phone listener**; **Goertzel**; band **18.5–19.5 kHz**, Phase 1 tone **19 kHz**. |
| 2026-07-04 | **WASAPI speaker auto-select**; **Gradle JDK 21** pin. |
| 2026-07-04 | Measurement UI + emitter `Burst N/M`; Step A peak **19000 Hz @ 57 dB**. |
| 2026-07-04 | Step B: **0.5 m 20/20**, **1.0 m 20/20**, **2.0 m 19/20 auto (95%)** / 20/20 manual. |
| 2026-07-04 | Step D: **0 false positives** (quiet room, 500 ms sustained hold). |
| 2026-07-04 | **Phase 2 FSK library** — Kotlin `fsk-core`, C# `SpaceDrop.AcousticFsk`, fixtures, POC `--mode fsk`. |
| 2026-07-04 | **Phase 2 CI gate PASS** — 100% clean fixture decode; bad-crc rejected. Manual live @ 2 m pending. |
| 2026-07-08 | **Capture-restart decode regression fixed** — gap gate now arms on absolute magnitude floor (`GAP_QUIET_MAGNITUDE=5e-5`) as well as SNR, so a decoder created mid-transmission arms without leading silence. `AudioToneListener` capture teardown reworked to per-session `AudioRecord` ownership + `cancelAndJoin` (no shared-field release race). Deterministic JVM repros added; `:fsk-core:test` green. |
| 2026-07-08 | **Restart fix LIVE CONFIRMED** — mid-transmission restarted session (PID 27134) decoded `0x5142E match=true` 14× continuously (pre-fix: silent). Separate finding logged: live decode reliability ~18% (14 good / 66 CRC rejects) due to symbol misalignment — tracked as its own item, blocks Phase 2 manual gate + Step 3. |
| 2026-07-08 | **Decode-reliability fix (symbol alignment)** — root cause: preamble->data start was estimated at block granularity (~46 ms), shifting every symbol window (bit-slip). Replaced with sub-block transition detection: after coarse preamble lock, a 1024-sample sliding window (128-sample hops) finds the exact 19 kHz->data onset and seeds the symbol clock there. Added centered-core symbol integration (drop 20% guard each edge) to absorb residual offset, and changed the gap-gate disarm to require data bins >3x the 19 kHz bin so a smeared gap->preamble straddle block no longer prevents lock. Deterministic phase-sweep repro added (`FskAlignmentTest`): 6/16 -> 16/16 offsets decode; full `:fsk-core:test` green. APK rebuilt+installed. **LIVE CONFIRMED** — ~57 decodes / ~10 rejects (was 14/66); ~100% frame success + zero rejects during a 2.5 min close-range streak; restart clean. Step 1 (restart bug + decode reliability) fully done. |
| 2026-07-08 | **Step 2 — architecture review** delivered as `docs/architecture-review-2026-07-08.md` (read-only). Verdict: discovery pipeline is already fusion-shaped, so acoustic is an additive third signal (add one field to `MergedDiscoveryCandidate` + one weight term), not a refactor. All four known-fragile areas (repo scope, transfer scope, `inFlightAddresses`, `AppState`) ruled **nice-to-have, not prerequisites** because the acoustic subsystem is self-contained (owns its own scope + `AudioRecord`). |
| 2026-07-08 | **Step 3a — `fsk-core` as Android module.** Rather than remapping the POC Gradle project (JVM toolchain 21 + JUnit 5, incompatible with the app's Java 17), a thin `android/fsk-core` module **source-includes** the POC `com.spacedrop.tier0.fsk` sources and compiles at `jvmTarget 17`. POC project left untouched; app consumes the exact live-verified decoder via `implementation(project(":fsk-core"))`. |
| 2026-07-08 | **Step 3c — fusion weight rebalance (before → after, max stays 1.0).** Before: BLE 0.35 + mDNS 0.20 + agreement 0.10 + BSSID 0.25 + host 0.10. After: BLE 0.35 + mDNS **0.15** + agreement **0.05** + BSSID **0.20** + host 0.10 + **acoustic 0.15**. Acoustic weight is carved from the identity/room family (mDNS/agreement/BSSID), not from BLE proximity, so distance ranking is preserved. Acoustic is **corroboration-only**: applied only when `acousticTokenMatch && (hasBle || hasMdns)` — it can never lift a phantom into a row. Matching computes `AcousticToken.computeToken20(deviceId, epoch)` for epoch and ±1 (clock-skew), freshness window 6 s. `FullFusionScorerTest` updated (mDNS-only now 0.25; full stack 1.0; acoustic-without-BLE/mDNS = 0). |
| 2026-07-08 | **Step 3e — tray emitter.** `AcousticEmitterService` folded into `SpaceDrop.Tray` (references `SpaceDrop.AcousticFsk` + NAudio). Token from tray `DeviceIdStore`, epoch from wall clock (30 s), continuous ~3.2 s frame+gap loop via WASAPI with speaker-preference selection; rebuilds the frame on epoch rollover so the phone's current-epoch(±1) computation always matches. |
| 2026-07-08 | **Step 4 — frontend wiring (no silent no-ops).** New `DeviceNameStore` (persisted, user-editable, defaults to `Build.MODEL`) drives PING `device_name` via a `deviceNameProvider` lambda threaded through `SpaceDropFileSender`/`SpaceDropHandshakeClient`. Settings: device-name rename dialog (real); "Appears as" + "Default receive folder" now honest info dialogs (Android is send-only). SavedDevices manage-action + Home QR pairing marked honestly not-yet-implemented (dialog/Toast). `onSendMore` resets transfer state and returns to Home. |
| 2026-07-08 | **Step 5 — UI.** (a) Home spinner↔radar flicker fixed: `FusedNearbyDeviceRepository.hasCompletedFirstRefresh` reserves the blocking spinner for a true cold start; recurring empty cycles keep the persistent searching radar. (b) Hybrid send UI: new `HybridDevicePicker` = AirDrop-style hero for the primary/most-used device + LocalSend-style scrollable list of secondaries; `RankedNearbyDevice.timesUsed` surfaces "Used N times" vs "New device"; reuses `AirDropDeviceAvatar` ring progress. |
| 2026-07-08 | **Step 6 — multi-file wire format.** One TCP connection carries the whole batch: `PING{count:N}` → `PONG`, then N × (`FILE_META` → raw bytes → `FILE_ACK`), then close; sender fails fast on first non-ok ACK. `count` is backward-compatible (absent ⇒ 1). Receiver reads control lines **byte-by-byte** off the raw socket (no `StreamReader` over-buffering into the next file's bytes); sender uses one persistent reader for its control replies since it never reads file bytes. Android: removed `items.size>1` guard + `multiFileWarning`; `sendFiles()` streams N files; Transfer Lab uses `OpenMultipleDocuments`; Share-Sheet `ACTION_SEND_MULTIPLE` path already parsed. Progress = overall batch bytes/percent + "file X of N". Duplicate names de-collided on the PC. Documented in `docs/protocol.md`. **Live 2+ file gate (both entry points) pending.** |

---

## How to update

1. Read [SKILL](../../.agents/skills/spacedrop-tier0/SKILL.md) before changing status.
2. Update **Last updated** and **STATUS** line at top when anything meaningful changes.
3. Change step status only to **DONE**, **IN PROGRESS**, or **NOT STARTED**.
4. Mark a phase gate **DONE** only when every step in that phase (including gate-declared row) is **DONE**.
5. Append findings and decisions; never edit old decision rows.
6. Put numbers in [`RESULTS.md`](RESULTS.md); keep PROGRESS as the dashboard.

---

## Next slice (Phase 2 manual)

1. Run live FSK @ 0.5 m and 2 m (20 frames) — fill [RESULTS.md](RESULTS.md) Phase 2 manual table.
2. Run 18.5 / 19.5 kHz smoke; note SNR vs 19 kHz baseline.
3. When manual gate PASS → declare Phase 2 **DONE** in PROGRESS; open Phase 3 planning.
