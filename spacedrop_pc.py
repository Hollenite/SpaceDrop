#!/usr/bin/env python3
"""SpaceDrop PC listener — mDNS advertisement and PING/PONG handshake.

Dev fallback only — prefer windows/SpaceDrop.Tray for normal use.
"""

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


def _get_default_route_ip() -> str | None:
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("8.8.8.8", 80))
        return probe.getsockname()[0]
    except OSError:
        return None
    finally:
        probe.close()


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


def _is_unusable_for_mdns(ip: str) -> bool:
    return ip.startswith("127.") or ip.startswith("169.254.")


def _is_windows_ics_hotspot(ip: str) -> bool:
    # Windows Mobile Hotspot / ICS uses 192.168.137.0/24 with the host at .1
    return ip == "192.168.137.1" or ip.startswith("192.168.137.")


def _ip_preference_score(ip: str) -> int:
    """Lower score = preferred when default-route IP is unavailable."""
    parts = ip.split(".")
    if len(parts) != 4:
        return 100
    if _is_unusable_for_mdns(ip):
        return 200
    if _is_windows_ics_hotspot(ip):
        return 180
    first, second = int(parts[0]), int(parts[1])
    if first == 192 and second == 168:
        return 0
    if first == 10:
        return 1
    if first == 172 and 16 <= second <= 31:
        return 2
    if first == 100 and 64 <= second <= 127:
        return 10  # CGNAT campus Wi-Fi or Tailscale — still routable
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

    default_route_ip = _get_default_route_ip()
    usable = [ip for ip in candidates if not _is_unusable_for_mdns(ip)]
    if not usable:
        raise RuntimeError("No usable local IPv4 address found")

    if default_route_ip and default_route_ip in usable:
        chosen = default_route_ip
        selection_reason = "default_route"
    else:
        chosen = min(usable, key=_ip_preference_score)
        selection_reason = "scored_fallback"

    if len(candidates) > 1:
        print(
            f"Detected IPs: {', '.join(candidates)} — using {chosen} for mDNS "
            f"({selection_reason})"
        )
    return chosen


def register_mdns(hostname: str, port: int, device_id: str) -> tuple[Zeroconf, ServiceInfo]:
    local_ip = get_local_ip()
    service_name = f"{hostname}.{SERVICE_TYPE}"
    info = ServiceInfo(
        SERVICE_TYPE,
        service_name,
        addresses=[socket.inet_aton(local_ip)],
        port=port,
        properties={
            "device_id": device_id.encode("utf-8"),
            "version": b"1",
        },
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


def read_exact(conn: socket.socket, size: int) -> bytes:
    buffer = bytearray()
    while len(buffer) < size:
        chunk = conn.recv(min(65536, size - len(buffer)))
        if not chunk:
            raise EOFError(f"Expected {size} bytes, got {len(buffer)}")
        buffer.extend(chunk)
    return bytes(buffer)


def send_json(conn: socket.socket, payload: dict) -> None:
    conn.sendall((json.dumps(payload) + "\n").encode("utf-8"))


def sanitize_filename(name: str) -> str:
    safe = "".join(c if c.isalnum() or c in "._-" else "_" for c in name)
    return safe[:200] or "received_file"


def handle_file_meta(conn: socket.socket, message: dict, peer_ip: str) -> None:
    if message.get("token") != SHARED_TOKEN:
        send_json(conn, {"type": "FILE_ACK", "ok": False, "error": "invalid token"})
        print(f"Rejected FILE_META from {peer_ip} — invalid token")
        return

    name = sanitize_filename(message.get("name", "received_file"))
    size = message.get("size")
    if not isinstance(size, int) or size < 0:
        send_json(conn, {"type": "FILE_ACK", "ok": False, "error": "invalid size"})
        return

    received_dir = Path(__file__).resolve().parent / "received"
    received_dir.mkdir(exist_ok=True)
    dest = received_dir / name

    print(f"Receiving {name} ({size} bytes) from {peer_ip}")
    try:
        if size == 0:
            data = b""
        else:
            data = read_exact(conn, size)
        dest.write_bytes(data)
        send_json(conn, {"type": "FILE_ACK", "ok": True, "path": str(dest)})
        print(f"Saved to {dest}")
    except (EOFError, OSError) as error:
        send_json(conn, {"type": "FILE_ACK", "ok": False, "error": str(error)})
        print(f"File receive failed from {peer_ip}: {error}")


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
    send_json(conn, pong)
    print(f"Connected to {peer_name} ({peer_id})")

    while True:
        try:
            line = read_line(conn)
        except (ConnectionResetError, BrokenPipeError):
            break
        if not line.strip():
            break
        try:
            next_message = json.loads(line)
        except json.JSONDecodeError:
            print(f"Invalid JSON after PONG from {peer_ip}")
            break
        msg_type = next_message.get("type")
        if msg_type == "FILE_META":
            handle_file_meta(conn, next_message, peer_ip)
        else:
            print(f"Unexpected message type from {peer_ip}: {msg_type!r}")
            break


def main() -> None:
    device_id = load_or_create_device_id()
    hostname = socket.gethostname()

    zeroconf: Zeroconf | None = None
    service_info: ServiceInfo | None = None
    server: socket.socket | None = None

    try:
        zeroconf, service_info = register_mdns(hostname, PORT, device_id)

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
