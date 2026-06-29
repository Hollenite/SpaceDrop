#!/usr/bin/env python3
"""SpaceDrop PC listener — mDNS advertisement and PING/PONG handshake."""

import json
import socket
import uuid
from pathlib import Path

from zeroconf import ServiceInfo, Zeroconf

PORT = 58392
SERVICE_TYPE = "_spacedrop._tcp.local."
# Placeholder shared secret for dev builds only — NOT real security.
# Will be replaced by a real pairing/trust step later.
SHARED_TOKEN = "spacedrop-dev-token"
DEVICE_ID_FILE = Path(__file__).resolve().parent / "device_id.txt"


def load_or_create_device_id() -> str:
    if DEVICE_ID_FILE.exists():
        device_id = DEVICE_ID_FILE.read_text(encoding="utf-8").strip()
        if device_id:
            print(f"Loaded device ID: {device_id}")
            return device_id
    device_id = str(uuid.uuid4())
    DEVICE_ID_FILE.write_text(device_id, encoding="utf-8")
    print(f"Generated new device ID: {device_id}")
    return device_id


def _ip_preference_score(ip: str) -> int:
    """Lower score = preferred for local mDNS advertisement."""
    parts = ip.split(".")
    if len(parts) != 4:
        return 100
    first, second = int(parts[0]), int(parts[1])
    if ip.startswith("127."):
        return 200
    if first == 192 and second == 168:
        return 0
    if first == 10:
        return 1
    if first == 172 and 16 <= second <= 31:
        return 2
    if first == 100 and 64 <= second <= 127:
        return 99  # Tailscale / CGNAT — deprioritize for Wi-Fi discovery
    return 50


def _collect_ipv4_candidates() -> list[str]:
    candidates: list[str] = []
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET, socket.SOCK_DGRAM):
            ip = info[4][0]
            if ip not in candidates:
                candidates.append(ip)
    except socket.gaierror:
        pass

    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("8.8.8.8", 80))
        default_ip = probe.getsockname()[0]
        if default_ip not in candidates:
            candidates.append(default_ip)
    except OSError:
        pass
    finally:
        probe.close()

    return candidates


def get_local_ip() -> str:
    candidates = _collect_ipv4_candidates()
    if not candidates:
        raise RuntimeError("Could not determine a local IPv4 address")

    chosen = min(candidates, key=_ip_preference_score)
    if len(candidates) > 1:
        print(f"Detected IPs: {', '.join(candidates)} — using {chosen} for mDNS")
    return chosen


def register_mdns(hostname: str, port: int) -> tuple[Zeroconf, ServiceInfo]:
    local_ip = get_local_ip()
    service_name = f"{hostname}.{SERVICE_TYPE}"
    info = ServiceInfo(
        SERVICE_TYPE,
        service_name,
        addresses=[socket.inet_aton(local_ip)],
        port=port,
        properties={},
    )
    zeroconf = Zeroconf()
    zeroconf.register_service(info)
    print(
        f"Registered mDNS service '{service_name}' at {local_ip}:{port}"
    )
    return zeroconf, info


def read_line(conn: socket.socket) -> str:
    buffer = bytearray()
    while True:
        chunk = conn.recv(1)
        if not chunk:
            break
        if chunk == b"\n":
            break
        buffer.extend(chunk)
    return buffer.decode("utf-8")


def handle_connection(
    conn: socket.socket,
    peer_ip: str,
    device_id: str,
    hostname: str,
) -> None:
    print(f"Connection attempt from {peer_ip}")

    try:
        line = read_line(conn)
        if not line.strip():
            print(f"Empty request from {peer_ip}")
            return

        message = json.loads(line)
    except (json.JSONDecodeError, UnicodeDecodeError):
        print(f"Invalid JSON from {peer_ip}")
        return

    if message.get("type") != "PING":
        print(f"Unexpected message type from {peer_ip}: {message.get('type')!r}")
        return

    token = message.get("token")
    if token != SHARED_TOKEN:
        print(f"Warning: rejected connection from {peer_ip} — invalid token")
        return

    peer_name = message.get("device_name", "unknown")
    peer_id = message.get("device_id", "unknown")

    pong = {
        "type": "PONG",
        "device_name": hostname,
        "device_id": device_id,
        "token": SHARED_TOKEN,
    }
    conn.sendall((json.dumps(pong) + "\n").encode("utf-8"))
    print(f"Connected to {peer_name} ({peer_id})")


def main() -> None:
    device_id = load_or_create_device_id()
    hostname = socket.gethostname()

    zeroconf: Zeroconf | None = None
    service_info: ServiceInfo | None = None
    server: socket.socket | None = None

    try:
        zeroconf, service_info = register_mdns(hostname, PORT)

        server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind(("0.0.0.0", PORT))
        server.listen()
        print(f"Listening on port {PORT}")
        print(
            "If manual connect fails instantly, allow inbound TCP port "
            f"{PORT} through Windows Firewall."
        )

        while True:
            conn, addr = server.accept()
            peer_ip = addr[0]
            try:
                handle_connection(conn, peer_ip, device_id, hostname)
            finally:
                conn.close()
    except KeyboardInterrupt:
        print("\nShutting down...")
    finally:
        if server is not None:
            server.close()
        if zeroconf is not None and service_info is not None:
            zeroconf.unregister_service(service_info)
            zeroconf.close()
        print("Stopped.")


if __name__ == "__main__":
    main()
