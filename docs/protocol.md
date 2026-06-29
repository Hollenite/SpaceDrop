# SpaceDrop Discovery Protocol (Tier 1 + Tier 2)

Shared constants for Android and Windows companions.

## TCP transfer (Tier 2 data plane)

- Port: **58392**
- Handshake: JSON line-delimited `PING` / `PONG`
- Transfer: `FILE_META` + raw bytes + `FILE_ACK`
- Dev token: `spacedrop-dev-token` (placeholder until real pairing)

## mDNS (Tier 2)

- Service type: `_spacedrop._tcp.local.`
- Port: **58392**

### TXT records

| Key | Value |
|---|---|
| `device_id` | UTF-8 UUID string (stable PC identity) |
| `version` | `1` |

Dedup key on Android: `device_id`.

## BLE GATT (Tier 1)

- Service UUID: `0000sd01-0000-1000-8000-00805f9b34fb`
- PC role: **peripheral** (advertiser + GATT server)
- Phone role: **central** (scanner + GATT reader during share flow)

### Characteristics

| Name | UUID | Properties | Payload |
|---|---|---|---|
| `deviceId` | `0000sd02-0000-1000-8000-00805f9b34fb` | Read | UTF-8 UUID string |
| `deviceName` | `0000sd03-0000-1000-8000-00805f9b34fb` | Read | UTF-8 hostname |
| `endpoint` | `0000sd04-0000-1000-8000-00805f9b34fb` | Read | UTF-8 JSON: `{"host":"100.x.x.x","port":58392}` |

### Advertisement

- Include 128-bit service UUID in advertisement data for scan filtering.
- Phone scans filtered by service UUID, connects, reads characteristics.

## Fusion (basic — Android)

Merge BLE + mDNS by `device_id`:

- Both signals → boost confidence, prefer mDNS host/port with BLE endpoint fallback
- mDNS only → Tier 2 fallback
- BLE only → show if RSSI ≥ −85 dBm using GATT endpoint
