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
