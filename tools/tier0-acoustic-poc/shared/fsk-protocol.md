# Tier 0 Phase 2 — FSK acoustic token protocol (v1)

Canonical wire format for PC → phone copresence token. Implemented in Kotlin (`fsk-core`) and C# (`SpaceDrop.AcousticFsk`).

## Frame layout

| Segment | Duration | Frequency | Notes |
|---------|----------|-----------|-------|
| Preamble | **300 ms** | **19000 Hz** | Sync + noise-floor settle |
| Data bit 0..23 | **100 ms each** | 18500 / 19500 Hz | MSB first |
| Inter-frame gap | **≥500 ms** | silence | gain = 0 |

**Total frame:** 300 + 2400 = **2700 ms** active tone, then ≥500 ms silence.

| Parameter | Value |
|-----------|--------|
| Sample rate | **44100 Hz** |
| Amplitude | **0.4** peak (POC default) |
| Bit 0 | **18500 Hz** |
| Bit 1 | **19500 Hz** |
| Preamble | **19000 Hz** |

## Payload (24 bits)

```
[ token19 .. token0 (20 bits) | crc3 .. crc0 (4 bits) ]
```

- **token20:** 20-bit acoustic token (see below)
- **crc4:** CRC-4 over the 20 token bits, MSB first

## Token computation

```
epoch = floor(unix_seconds / 30)
input = UTF8(device_id) || epoch as uint32 big-endian (4 bytes)
hash = SHA-256(input)
token20 = (hash[0] << 12) | (hash[1] << 4) | (hash[2] >> 4)   // top 20 bits of hash
```

**Example** (test vector):

| Field | Value |
|-------|--------|
| device_id | `550e8400-e29b-41d4-a716-446655440000` |
| epoch | `100` |
| token20 (hex) | `0x5142E` (verify in unit tests) |

## CRC-4

- Polynomial: **0x3** (x^4 + x + 1)
- Init: **0**
- Process 20 token bits MSB first (bit 19 → bit 0)
- Result is 4-bit remainder appended MSB first (crc3..crc0)

```python
# Reference (Python)
def crc4_20bits(token20):
    crc = 0
    for i in range(19, -1, -1):
        bit = (token20 >> i) & 1
        crc ^= bit
        if crc & 8:
            crc = ((crc << 1) ^ 3) & 0xF
        else:
            crc = (crc << 1) & 0xF
    return crc
```

## Decoder rules

1. **Gap gate:** a block counts as quiet when SNR @ 19 kHz `< 8 dB` **or** `mag(19000) < 5×10⁻⁵` (absolute floor); **200 ms** quiet arms the lock; **disarm** only on *loud* data symbols — data bins must exceed the 19 kHz bin by **3×** (a smeared gap→preamble straddle block sits near 1× and must not disarm, or a preamble onset landing late in a block could never lock). The absolute floor lets a decoder created mid-transmission (capture restart) still arm even though its noise floor never saw leading silence.
2. **Idle → Preamble:** triple-bin onset when armed — SNR ≥ **20 dB**, `mag(19000) ≥ 2×10⁻⁴`, dominates 18.5/19.5 kHz by **1.3×**, **2 blocks** debounce.
3. **Preamble → Data (sub-block alignment):** after lock, slide a **1024-sample** window (**128-sample** hops) over 19 kHz vs 18.5/19.5 kHz; once data clearly dominates (**1.2×**, having first seen preamble dominate), take that as the data onset and start the symbol clock there. This aligns to the true onset instead of the block grid, eliminating symbol bit-slip. (Replaces the old block-granular `300 ms − debounce×46 ms` estimate.)
4. **Data:** each **100 ms** window, integrate only the **centered 60%** (drop 20% guard each edge to tolerate residual clock offset); bit = 1 if `mag(19500) > mag(18500) × 1.2`, else 0.
5. **Validate:** recompute CRC-4 on decoded token20; emit frame on match.
6. **After CRC success:** **500 ms** cooldown with gap analysis; preamble lock allowed during cooldown.
7. **After CRC reject:** return to Idle immediately (no cooldown); require fresh gap before re-lock.

## Rotation

Token rotates every **30 s** with `epoch`. Phase 2 libraries take `(deviceId, epoch)` explicitly. Phase 3 tray uses wall clock.

## Phase 2 exit gate (automated)

- Kotlin round-trip encode/decode: **100%** on clean synthesis
- C# encode → Kotlin decode on golden WAV fixtures: **100%**
- `bad-crc` / wrong-epoch fixtures: **0** valid decodes

## Phase 2 exit gate (manual)

- Live @ 2 m: **≥90%** frames decode over 20 transmissions (Acer + Xiaomi)
