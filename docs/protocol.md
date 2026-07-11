# SpaceDrop Discovery Protocol (Tier 1 + Tier 2)

Shared constants for Android and Windows companions.

## TCP transfer (Tier 2 data plane)

- Port: **58392**
- Handshake: JSON line-delimited `PING` / `PONG`
- Transfer: `FILE_META` + raw bytes + `FILE_ACK`
- Dev token: `spacedrop-dev-token` (placeholder until real pairing)

### Multi-file transfer (one connection, explicit count)

A single TCP connection carries a whole batch of 1..N files. The sender opens one socket,
does one `PING`/`PONG`, then repeats `FILE_META → raw bytes → FILE_ACK` once per file, in order,
and closes the socket when done. Fails fast: the sender aborts the batch on the first non-`ok`
`FILE_ACK` or socket error.

- `PING` carries a `count` field = number of files in the batch (defaults to `1` if absent, for
  backward compatibility). The receiver uses it to know how many `FILE_META` cycles to expect and
  stops after that many (it also stops on connection close / empty line).
- Each `FILE_META` is exactly as before (`name`, `size`, `mime`, `token`); the receiver reads
  precisely `size` bytes of raw payload after it, then replies `FILE_ACK`.
- Control lines (`PONG`, every `FILE_ACK`) are newline-delimited JSON. The **receiver reads control
  lines byte-by-byte** off the raw socket (not a buffered reader) so it never reads ahead into the
  next file's raw bytes. The sender only writes file bytes (never reads them), so a single buffered
  reader for all its control replies is safe.
- Duplicate filenames within a batch are de-collided on the receiver by appending ` (n)` before the
  extension.

```
PING {count:N} → PONG
  ├─ FILE_META(f1) → <size1 bytes> → FILE_ACK(ok)
  ├─ FILE_META(f2) → <size2 bytes> → FILE_ACK(ok)
  └─ … up to fN → close
```

Progress is reported as **overall** batch bytes/percent plus the current file index/name
(`file X of N`), not a single ambiguous per-file bar.

## mDNS (Tier 2)

- Service type: `_spacedrop._tcp.local.`
- Port: **58392**

### TXT records

| Key | Value |
|---|---|
| `device_id` | UTF-8 UUID string (stable PC identity) |
| `version` | `1` |
| `bssid` | Optional lowercase Wi-Fi AP BSSID, e.g. `aa:bb:cc:dd:ee:ff` (omit on Ethernet-only) |

Dedup key on Android: `device_id`.

## BLE GATT (Tier 1)

- Service UUID: `0000a501-0000-1000-8000-00805f9b34fb`
- PC role: **peripheral** (advertiser + GATT server)
- Phone role: **central** (scanner + GATT reader during share flow)

### Characteristics

| Name | UUID | Properties | Payload |
|---|---|---|---|
| `deviceId` | `0000a502-0000-1000-8000-00805f9b34fb` | Read | UTF-8 UUID string |
| `deviceName` | `0000a503-0000-1000-8000-00805f9b34fb` | Read | UTF-8 hostname |
| `endpoint` | `0000a504-0000-1000-8000-00805f9b34fb` | Read | UTF-8 JSON: `{"host":"100.x.x.x","port":58392}` |

### Advertisement

- Include 128-bit service UUID in advertisement data for scan filtering.
- Phone scans filtered by service UUID, connects, reads characteristics.

## Fusion (full — Android)

Merge BLE + mDNS by `device_id`, then score with weighted signals:

| Signal | Weight | Notes |
|--------|--------|-------|
| BLE RSSI tier | 0.0–0.35 | −65 / −80 / −85 dBm thresholds |
| mDNS present | 0.20 | PC reachable on LAN |
| BLE + mDNS agree | 0.10 | Same `device_id` |
| BSSID match | 0.25 | Phone connected AP BSSID == PC advertised `bssid` TXT |
| Host resolvable | 0.10 | Transfer-ready endpoint |

Cap confidence at **1.0**. Prefer mDNS host/port with BLE endpoint fallback.

Visibility rules:

- BLE-only hidden below RSSI −85 dBm unless mDNS corroborates
- mDNS-only shown as Tier 2 fallback
- BSSID unavailable on either side → skip Wi-Fi vote (no penalty)
