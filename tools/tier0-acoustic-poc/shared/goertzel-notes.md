# Goertzel algorithm — shared notes (Tier 0 POC)

Used by the Android detector (Phase 1) and future FSK decode (Phase 2).

## Why Goertzel

- Evaluates one DFT bin at frequency `f` in **O(N)** time, **O(1)** space.
- Equivalent to a single FFT bin (within floating-point rounding).
- For two FSK tones we run two Goertzel passes per block — cheaper than full FFT.

## Parameters

| Symbol | Meaning | Phase 1 default |
|--------|---------|-----------------|
| `fs` | Sample rate (Hz) | 44100 or 48000 |
| `f` | Target frequency (Hz) | 17000–21000 (UI slider) |
| `N` | Block size (samples) | 2048 (~46 ms @ 44.1 kHz) |
| `k` | Bin index | `round(N * f / fs)` |

## Coefficient

```
ω = 2π · k / N
coeff = 2 · cos(ω)
```

## Recurrence (per sample `x[n]`, normalized to [-1, 1])

```
s[n] = x[n] + coeff · s[n-1] - s[n-2]
```

After N samples:

```
real = s[N-1] - s[N-2] · cos(ω)
imag = s[N-2] · sin(ω)
magnitude = sqrt(real² + imag²) / N
```

## Detection (Phase 1)

- Maintain a **noise floor** = exponential moving average of magnitude when not detecting.
- **SNR estimate** = `20 · log10(magnitude / noiseFloor)`.
- **DETECTED** when SNR > 12 dB (configurable) for ≥ 200 ms consecutive blocks.

## Nyquist

At `fs = 44100`, Nyquist = 22050 Hz — 19 kHz is safely below the limit.

## Multipath (Phase 2)

Indoor delay spread ~70 ms → FSK symbol duration ≥ 80–100 ms; preamble sync tone ~300 ms.
