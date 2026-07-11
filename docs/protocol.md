# SpaceDrop Discovery Protocol (Tier 1 + Tier 2)

Shared constants for Android and Windows companions.

**Security model (v2):** See [adr/0001-pairing-and-tls.md](adr/0001-pairing-and-tls.md).  
Transfers require prior QR pairing and mutual TLS. The legacy shared token is retired.

---

## TCP / TLS transfer (Tier 2 data plane)

- Port: **58392**
- Transport: **TLS 1.3** with mutual X.509 authentication (ECDSA P-256 identity certs)
- Application framing (inside TLS): JSON line-delimited `PING` / `PONG`, then `FILE_META` + raw bytes + `FILE_ACK`
- Peer authorization: each side’s certificate SHA-256 fingerprint must appear in the local **paired peer** store
- Unpaired or non-TLS clients are rejected

### Pairing (before first transfer)

1. PC shows a QR (`spacedrop://pair?...`) containing `id`, cert fingerprint `fp`, `port`, nonce, expiry, and ECDSA `sig`.
2. Phone scans, verifies `sig` + `exp`, connects with TLS to the discovered host, verifies server cert fingerprint == `fp`.
3. Phone sends a `PAIR_REQUEST` JSON line (device_id, display name) over TLS; PC prompts Accept/Decline.
4. On accept, both persist the peer fingerprint and allow future transfers.

### Multi-file transfer (one TLS connection, explicit count)

```
TLS handshake (mutual auth against paired store)
PING {count:N, device_id, device_name} → PONG
  FILE_META(f1) → <size1 bytes> → FILE_ACK(ok)
  …
  close
```

- `PING.count` = number of files (default `1` if absent).
- No shared-token field. Authorization is the TLS peer identity.
- Control lines are newline-delimited JSON; the receiver reads control lines **byte-by-byte** off the decrypted stream so it never over-buffers into raw file bytes.
- Duplicate filenames are de-collided with ` (n)` before the extension.
- Receiver enforces max file size / batch size and writes only under the receive directory (canonical path check).

### Acoustic arm over TCP (fallback)

When BLE peripheral mode is unavailable, the phone may open a short-lived TLS or plaintext control connection — **v2 prefers BLE**. TCP fallback message (plaintext, rate-limited, does not authorize transfer):

```json
{"type":"ACOUSTIC_ARM","duration_ms":20000}
→ {"type":"ACOUSTIC_ACK","ok":true}
```

---

## On-demand acoustic (Tier 0 corroboration)

- PC default: **silent** (no continuous emit).
- Phone discovery UI open → ARM nearby PCs for ~20 s → PC emits FSK token loop → phone mic matches → fusion boost.
- ARM never creates a device row and never grants transfer rights.

### BLE control characteristic

| Name | UUID | Properties | Payload |
|------|------|------------|---------|
| `control` | `0000a505-0000-1000-8000-00805f9b34fb` | Write | UTF-8 JSON |

ARM example: `{"op":"ARM","duration_ms":20000}`  
DISARM example: `{"op":"DISARM"}`  

Max `duration_ms`: 60000. Malformed / overlong requests are ignored.

---

## mDNS (Tier 2)

- Service type: `_spacedrop._tcp.local.`
- Port: **58392**

### TXT records

| Key | Value |
|---|---|
| `device_id` | UTF-8 UUID string (stable PC identity) |
| `version` | `2` |
| `fp` | Optional lowercase SHA-256 of PC identity cert (hex), aids discovery UI |
| `bssid` | Optional lowercase Wi-Fi AP BSSID |

Dedup key on Android: `device_id`.

---

## BLE GATT (Tier 1)

- Service UUID: `0000a501-0000-1000-8000-00805f9b34fb`
- PC role: **peripheral** (advertiser + GATT server)
- Phone role: **central** (scanner + GATT read/write during discovery)

### Characteristics

| Name | UUID | Properties | Payload |
|---|---|---|---|
| `deviceId` | `0000a502-0000-1000-8000-00805f9b34fb` | Read | UTF-8 UUID string |
| `deviceName` | `0000a503-0000-1000-8000-00805f9b34fb` | Read | UTF-8 hostname |
| `endpoint` | `0000a504-0000-1000-8000-00805f9b34fb` | Read | UTF-8 JSON: `{"host":"…","port":58392}` |
| `control` | `0000a505-0000-1000-8000-00805f9b34fb` | Write | UTF-8 JSON ARM/DISARM |

### Advertisement

- Include 128-bit service UUID in advertisement data for scan filtering.
- Phone scans filtered by service UUID, connects, reads characteristics, may write `control`.

---

## QR pairing URL

```
spacedrop://pair?v=1&id=<uuid>&fp=<64_hex>&port=58392&n=<16+_hex>&exp=<unix>&sig=<base64url>
```

Canonical string signed by PC identity key (ECDSA P-256, SHA-256):

```
v=1&id=<id>&fp=<fp>&port=<port>&n=<n>&exp=<exp>
```

---

## Paired peer store

JSON array (both platforms):

```json
[
  {
    "deviceId": "…",
    "fingerprint": "…",
    "displayName": "…",
    "pairedAtUnix": 1710000000,
    "alwaysAllow": true
  }
]
```

`TrustState.PAIRED` is set only when `deviceId` is in this store.

---

## Fusion (full — Android)

Merge BLE + mDNS by `device_id`, then score:

| Signal | Weight | Notes |
|--------|--------|-------|
| BLE RSSI tier | 0.0–0.35 | −65 / −80 / −85 dBm thresholds |
| mDNS present | 0.15 | PC reachable on LAN |
| BLE + mDNS agree | 0.05 | Same `device_id` |
| BSSID match | 0.20 | Phone AP BSSID == PC TXT |
| Host resolvable | 0.10 | Transfer-ready endpoint |
| Acoustic token match | 0.15 | Only after ARM window; corroboration only |

Cap confidence at **1.0**. Prefer mDNS host/port with BLE endpoint fallback.

Visibility rules:

- BLE-only hidden below RSSI −85 dBm unless mDNS corroborates
- mDNS-only shown as Tier 2 fallback
- Acoustic alone never creates a row
- Transfer requires paired peer + TLS, independent of fusion score
