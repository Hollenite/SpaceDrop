# Phase 1 gate decision guide

Use after completing [RESULTS.md](RESULTS.md) measurements.

## If overall gate is **PASS**

1. Mark all exit-gate rows PASS in RESULTS.md.
2. Create GitHub issue:

   **Title:** `Tier 0 Phase 2 — FSK token encode/decode library`

   **Body (template):**

   ```
   Phase 1 acoustic POC passed on [hardware].

   - Peak frequency: ___ Hz
   - 2 m burst detection rate: ___%
   - False positives (60 s silent): 0

   Next: FSK preamble + 20-bit token + CRC, Kotlin + C# with WAV fixture unit tests.
   Do not wire into main app until Phase 2 decode accuracy gate passes.

   Plan reference: tools/tier0-acoustic-poc/README.md
   ```

3. Paste issue URL into RESULTS.md under **Phase 2 issue**.

## If overall gate is **FAIL**

Do **not** open Phase 2. Document in RESULTS.md which criterion failed, then try fallbacks in order:

| Order | Fallback | When |
|-------|----------|------|
| 1 | Elevate laptop / unobstruct bottom-firing speakers | Weak SNR at all freqs ≥ 18 kHz |
| 2 | External USB speaker on PC, re-run Steps A–D | Built-in speaker rolloff |
| 3 | Lower band test (16–17 kHz), note human audibility | Still weak above 18 kHz |
| 4 | **Stop Tier 0** — rely on Tier 1 BLE + Tier 2 mDNS only | All acoustic paths fail |

Record final decision in RESULTS.md **Decision notes** section.

## If gate is still **PENDING**

POC build verified:

- Windows: `dotnet build` in `windows/AcousticEmitter/` (requires nuget.org)
- Android: `android/gradlew -p tools/tier0-acoustic-poc/android assembleDebug` (JDK 17/21 recommended)

Run measurement protocol on device, then update RESULTS.md.
