# ADR 0001 — Pairing, TLS, and on-demand acoustic

**Status:** Accepted  
**Date:** 2026-07-11  
**Context:** SpaceDrop 0.1 used a hardcoded LAN token, plaintext TCP, continuous ultrasonic emit, and cosmetic “paired” trust. This ADR records the P2 security model.

## Decision

### 1. On-demand acoustic (silent by default)

- The PC speaker stays **idle** while the tray runs.
- When the phone’s discovery UI is open, it **arms** nearby PCs for a short window (~20 s) via BLE GATT write (primary) or TCP `ACOUSTIC_ARM` (fallback).
- Arming only permits ultrasonic corroboration. It does **not** authorize file transfer.

### 2. Identity

- Each device has a stable `device_id` (UUID) and a long-lived **ECDSA P-256** identity keypair.
- A self-signed **X.509** certificate is derived from that key for TLS 1.3.
- PC private key: `%LOCALAPPDATA%\SpaceDrop\identity.pfx` (user-protected).
- Phone private key: app-private encrypted storage / Keystore-backed where practical.

### 3. QR pairing (PC displays, phone scans)

QR URL:

```
spacedrop://pair?v=1&id=<pc_device_id>&fp=<sha256_cert_fingerprint_hex>&port=58392&n=<nonce_hex>&exp=<unix>&sig=<base64url_ecdsa>
```

- `fp` pins the PC certificate fingerprint (MITM-resistant when the user scans the on-screen QR).
- `sig` is ECDSA over the canonical offer fields with the PC identity key.
- Phone verifies signature and expiry, connects with TLS, checks server cert fingerprint == `fp`, presents its own client certificate, PC shows Accept/Decline.
- Both sides persist `PairedPeer{ deviceId, fingerprint, displayName, pairedAt }`.

### 4. Transfer after pairing

- Port **58392**, **TLS 1.3 only**.
- Mutual certificate authentication; peer fingerprint must be in the local paired store.
- JSON multi-file framing (`PING` / `FILE_META` / …) runs **inside** TLS.
- `spacedrop-dev-token` is removed from the production path.

### 5. Threat model (in scope)

Trusted operator scans the real PC QR once. Afterwards, a LAN attacker cannot:

- decrypt transfers,
- impersonate the paired PC to the phone,
- impersonate the paired phone to the PC,

without stealing a private key.

Discovery (BLE / mDNS / acoustic) remains cleartext and unauthenticated; it must never authorize writes.

## Consequences

- Unpaired phones cannot complete transfers.
- Continuous ultrasonic emit is gone.
- Privacy / pairing UI must match this model (no fake “already paired”).
