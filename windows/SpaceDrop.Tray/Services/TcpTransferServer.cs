using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using SpaceDrop.Tray.Protocol;

namespace SpaceDrop.Tray.Services;

public sealed class TcpTransferServer : IAsyncDisposable
{
    private readonly IdentityKeyStore _identity;
    private readonly PairedPeerStore _peers;
    private readonly string _hostname;
    private readonly string _receivedDir;
    private readonly Func<bool> _isPairingWindowOpen;
    private readonly Func<string, string, bool> _confirmPairing;
    private readonly Func<string, string, long, bool> _confirmReceive;
    private readonly Action<int>? _onAcousticArm;
    private TcpListener? _listener;
    private CancellationTokenSource? _cts;

    public string ReceivedDirectory { get; }

    public TcpTransferServer(
        IdentityKeyStore identity,
        PairedPeerStore peers,
        string hostname,
        Func<bool> isPairingWindowOpen,
        Func<string, string, bool> confirmPairing,
        Func<string, string, long, bool> confirmReceive,
        Action<int>? onAcousticArm = null)
    {
        _identity = identity;
        _peers = peers;
        _hostname = hostname;
        _isPairingWindowOpen = isPairingWindowOpen;
        _confirmPairing = confirmPairing;
        _confirmReceive = confirmReceive;
        _onAcousticArm = onAcousticArm;
        _receivedDir = ReceivedPathResolver.Resolve();
        Directory.CreateDirectory(_receivedDir);
        ReceivedDirectory = _receivedDir;
        Console.WriteLine($"Received files directory: {_receivedDir}");
    }

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        await StopAsync();
        _cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        _listener = new TcpListener(IPAddress.Any, SpaceDropProtocol.Port);
        _listener.Start();
        Console.WriteLine($"TLS listening on port {SpaceDropProtocol.Port}");
        _ = AcceptLoopAsync(_cts.Token);
        await Task.CompletedTask;
    }

    private async Task AcceptLoopAsync(CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested && _listener is not null)
        {
            try
            {
                var client = await _listener.AcceptTcpClientAsync(cancellationToken);
                var peerIp = ((IPEndPoint)client.Client.RemoteEndPoint!).Address.ToString();
                _ = Task.Run(() => HandleConnectionAsync(client, peerIp, cancellationToken), cancellationToken);
            }
            catch (OperationCanceledException) { break; }
            catch (Exception ex) { Console.WriteLine($"Accept error: {ex.Message}"); }
        }
    }

    private async Task HandleConnectionAsync(TcpClient client, string peerIp, CancellationToken cancellationToken)
    {
        using var _ = client;
        Console.WriteLine($"Connection from {peerIp}");

        try
        {
            await using var network = client.GetStream();

            // Plaintext acoustic arm fallback (first line JSON) before TLS — rate-limited speaker only.
            if (await TryHandlePlainAcousticArmAsync(client, network, peerIp, cancellationToken))
            {
                return;
            }

            await using var ssl = new SslStream(network, leaveInnerStreamOpen: false, ValidateClientCertificate);
            var options = new SslServerAuthenticationOptions
            {
                ServerCertificate = _identity.Certificate,
                ClientCertificateRequired = true,
                EnabledSslProtocols = SslProtocols.Tls13 | SslProtocols.Tls12,
                CertificateRevocationCheckMode = X509RevocationMode.NoCheck
            };
            await ssl.AuthenticateAsServerAsync(options, cancellationToken);

            var clientCert = ssl.RemoteCertificate as X509Certificate2
                ?? (ssl.RemoteCertificate != null ? new X509Certificate2(ssl.RemoteCertificate) : null);
            if (clientCert is null)
            {
                Console.WriteLine($"Rejected {peerIp}: no client certificate");
                return;
            }

            var clientFp = IdentityKeyStore.FingerprintOf(clientCert);
            var paired = _peers.FindByFingerprint(clientFp);
            var pairingOpen = _isPairingWindowOpen();

            if (paired is null && !pairingOpen)
            {
                Console.WriteLine($"Rejected {peerIp}: unpaired fingerprint {clientFp[..16]}…");
                return;
            }

            await using var writer = new StreamWriter(ssl, Encoding.UTF8, leaveOpen: true) { AutoFlush = true };
            var line = await ReadLineAsync(ssl, cancellationToken);
            if (string.IsNullOrWhiteSpace(line)) return;

            using var doc = JsonDocument.Parse(line);
            var root = doc.RootElement;
            var type = root.GetProperty("type").GetString();

            if (type == "PAIR_REQUEST")
            {
                await HandlePairRequestAsync(root, clientFp, writer, peerIp);
                return;
            }

            if (type == "ACOUSTIC_ARM")
            {
                var ms = root.TryGetProperty("duration_ms", out var d) && d.TryGetInt32(out var v)
                    ? v : SpaceDropProtocol.DefaultAcousticArmMs;
                ms = Math.Clamp(ms, 1000, SpaceDropProtocol.MaxAcousticArmMs);
                _onAcousticArm?.Invoke(ms);
                await writer.WriteLineAsync(JsonSerializer.Serialize(new { type = "ACOUSTIC_ACK", ok = true }));
                return;
            }

            if (type != "PING")
            {
                Console.WriteLine($"Unexpected type from {peerIp}: {type}");
                return;
            }

            if (paired is null)
            {
                await writer.WriteLineAsync(JsonSerializer.Serialize(new
                {
                    type = "PONG",
                    ok = false,
                    error = "not paired"
                }));
                return;
            }

            var peerName = root.TryGetProperty("device_name", out var nameEl)
                ? nameEl.GetString() ?? paired.DisplayName
                : paired.DisplayName;
            var peerId = root.TryGetProperty("device_id", out var idEl)
                ? idEl.GetString() ?? paired.DeviceId
                : paired.DeviceId;
            var expectedCount = root.TryGetProperty("count", out var countEl) && countEl.TryGetInt32(out var c)
                ? c : 1;

            await writer.WriteLineAsync(JsonSerializer.Serialize(new
            {
                type = "PONG",
                ok = true,
                device_name = _hostname,
                device_id = _identity.DeviceId,
                fingerprint = _identity.FingerprintHex
            }));

            Console.WriteLine($"TLS transfer from {peerName} ({peerId}) — {expectedCount} file(s)");

            var received = 0;
            long batchBytes = 0;
            while (received < expectedCount)
            {
                var nextLine = await ReadLineAsync(ssl, cancellationToken);
                if (string.IsNullOrWhiteSpace(nextLine)) break;

                using var nextDoc = JsonDocument.Parse(nextLine);
                var next = nextDoc.RootElement;
                if (next.GetProperty("type").GetString() != "FILE_META")
                {
                    Console.WriteLine($"Unexpected message: {next.GetProperty("type").GetString()}");
                    break;
                }

                var size = next.GetProperty("size").GetInt64();
                batchBytes += Math.Max(0, size);
                if (size > SpaceDropProtocol.MaxFileBytes || batchBytes > SpaceDropProtocol.MaxBatchBytes)
                {
                    await writer.WriteLineAsync(JsonSerializer.Serialize(new
                    {
                        type = "FILE_ACK",
                        ok = false,
                        error = "file or batch too large"
                    }));
                    break;
                }

                var fileName = next.GetProperty("name").GetString() ?? "file";
                if (!paired.AlwaysAllow)
                {
                    var allowed = _confirmReceive(peerName, fileName, size);
                    if (!allowed)
                    {
                        await writer.WriteLineAsync(JsonSerializer.Serialize(new
                        {
                            type = "FILE_ACK",
                            ok = false,
                            error = "declined by user"
                        }));
                        break;
                    }
                }

                await HandleFileMetaAsync(next, ssl, writer, peerIp, received + 1, expectedCount);
                received++;
            }

            Console.WriteLine($"Transfer complete {received}/{expectedCount} from {peerName}");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Connection error from {peerIp}: {ex.Message}");
        }
    }

    /// <summary>
    /// Peek: if the client sends plaintext JSON ACOUSTIC_ARM (not a TLS ClientHello), handle and close.
    /// TLS ClientHello starts with 0x16.
    /// </summary>
    private async Task<bool> TryHandlePlainAcousticArmAsync(
        TcpClient client,
        NetworkStream network,
        string peerIp,
        CancellationToken cancellationToken)
    {
        if (!network.DataAvailable) return false;
        client.Client.Blocking = false;
        var peek = new byte[1];
        var got = client.Client.Receive(peek, 0, 1, SocketFlags.Peek);
        client.Client.Blocking = true;
        if (got <= 0) return false;
        if (peek[0] == 0x16) return false; // TLS

        var line = await ReadLineAsync(network, cancellationToken);
        if (string.IsNullOrWhiteSpace(line)) return true;
        try
        {
            using var doc = JsonDocument.Parse(line);
            if (doc.RootElement.GetProperty("type").GetString() != "ACOUSTIC_ARM") return true;
            var ms = doc.RootElement.TryGetProperty("duration_ms", out var d) && d.TryGetInt32(out var v)
                ? v : SpaceDropProtocol.DefaultAcousticArmMs;
            ms = Math.Clamp(ms, 1000, SpaceDropProtocol.MaxAcousticArmMs);
            _onAcousticArm?.Invoke(ms);
            var ack = Encoding.UTF8.GetBytes(
                JsonSerializer.Serialize(new { type = "ACOUSTIC_ACK", ok = true }) + "\n");
            await network.WriteAsync(ack, cancellationToken);
            Console.WriteLine($"Plain ACOUSTIC_ARM from {peerIp} for {ms} ms");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Plain control parse failed from {peerIp}: {ex.Message}");
        }
        return true;
    }

    private bool ValidateClientCertificate(
        object sender,
        X509Certificate? certificate,
        X509Chain? chain,
        SslPolicyErrors sslPolicyErrors)
    {
        // We pin fingerprints ourselves; accept self-signed client certs.
        return certificate is not null;
    }

    private async Task HandlePairRequestAsync(
        JsonElement root,
        string clientFp,
        StreamWriter writer,
        string peerIp)
    {
        if (!_isPairingWindowOpen())
        {
            await writer.WriteLineAsync(JsonSerializer.Serialize(new
            {
                type = "PAIR_RESULT",
                ok = false,
                error = "pairing window closed"
            }));
            return;
        }

        var deviceId = root.TryGetProperty("device_id", out var idEl) ? idEl.GetString() ?? "" : "";
        var name = root.TryGetProperty("device_name", out var nameEl) ? nameEl.GetString() ?? "Phone" : "Phone";
        if (string.IsNullOrWhiteSpace(deviceId))
        {
            await writer.WriteLineAsync(JsonSerializer.Serialize(new
            {
                type = "PAIR_RESULT",
                ok = false,
                error = "missing device_id"
            }));
            return;
        }

        var accepted = _confirmPairing(name, deviceId);
        if (!accepted)
        {
            await writer.WriteLineAsync(JsonSerializer.Serialize(new
            {
                type = "PAIR_RESULT",
                ok = false,
                error = "declined"
            }));
            Console.WriteLine($"Pairing declined for {name} from {peerIp}");
            return;
        }

        _peers.Upsert(new PairedPeer
        {
            DeviceId = deviceId,
            Fingerprint = clientFp,
            DisplayName = name,
            PairedAtUnix = DateTimeOffset.UtcNow.ToUnixTimeSeconds(),
            AlwaysAllow = true
        });

        await writer.WriteLineAsync(JsonSerializer.Serialize(new
        {
            type = "PAIR_RESULT",
            ok = true,
            device_id = _identity.DeviceId,
            device_name = _hostname,
            fingerprint = _identity.FingerprintHex
        }));
        Console.WriteLine($"Paired with {name} ({deviceId}) fp={clientFp[..16]}…");
    }

    private async Task HandleFileMetaAsync(
        JsonElement message,
        Stream stream,
        StreamWriter writer,
        string peerIp,
        int fileIndex,
        int fileCount)
    {
        var name = SanitizeFilename(message.GetProperty("name").GetString() ?? "received_file");
        var size = message.GetProperty("size").GetInt64();
        if (size < 0 || size > SpaceDropProtocol.MaxFileBytes)
        {
            await writer.WriteLineAsync(JsonSerializer.Serialize(new
            {
                type = "FILE_ACK",
                ok = false,
                error = "invalid size"
            }));
            return;
        }

        var dest = UniqueDestination(Path.Combine(_receivedDir, name));
        if (!IsUnderReceivedDir(dest))
        {
            await writer.WriteLineAsync(JsonSerializer.Serialize(new
            {
                type = "FILE_ACK",
                ok = false,
                error = "invalid path"
            }));
            return;
        }

        Console.WriteLine($"Receiving file {fileIndex}/{fileCount}: {name} ({size} bytes) from {peerIp}");

        try
        {
            await using var fileStream = File.Create(dest);
            var buffer = new byte[65536];
            var remaining = size;
            while (remaining > 0)
            {
                var toRead = (int)Math.Min(buffer.Length, remaining);
                var read = await stream.ReadAsync(buffer.AsMemory(0, toRead));
                if (read == 0) throw new EndOfStreamException($"Expected {size} bytes");
                await fileStream.WriteAsync(buffer.AsMemory(0, read));
                remaining -= read;
            }

            await writer.WriteLineAsync(JsonSerializer.Serialize(new
            {
                type = "FILE_ACK",
                ok = true,
                path = dest
            }));
            Console.WriteLine($"Saved to {dest}");
        }
        catch (Exception ex)
        {
            await writer.WriteLineAsync(JsonSerializer.Serialize(new
            {
                type = "FILE_ACK",
                ok = false,
                error = ex.Message
            }));
            Console.WriteLine($"File receive failed from {peerIp}: {ex.Message}");
        }
    }

    private bool IsUnderReceivedDir(string path)
    {
        var full = Path.GetFullPath(path);
        var root = Path.GetFullPath(_receivedDir)
            .TrimEnd(Path.DirectorySeparatorChar) + Path.DirectorySeparatorChar;
        return full.StartsWith(root, StringComparison.OrdinalIgnoreCase) ||
               string.Equals(full, Path.GetFullPath(_receivedDir), StringComparison.OrdinalIgnoreCase);
    }

    private static async Task<string?> ReadLineAsync(Stream stream, CancellationToken cancellationToken)
    {
        var bytes = new List<byte>(128);
        var one = new byte[1];
        while (true)
        {
            var read = await stream.ReadAsync(one.AsMemory(0, 1), cancellationToken);
            if (read == 0) return bytes.Count == 0 ? null : Encoding.UTF8.GetString(bytes.ToArray());
            if (one[0] == (byte)'\n') break;
            if (one[0] == (byte)'\r') continue;
            bytes.Add(one[0]);
            if (bytes.Count > 1_000_000) throw new InvalidOperationException("Line too long");
        }
        return Encoding.UTF8.GetString(bytes.ToArray());
    }

    private static string UniqueDestination(string path)
    {
        if (!File.Exists(path)) return path;
        var dir = Path.GetDirectoryName(path) ?? string.Empty;
        var stem = Path.GetFileNameWithoutExtension(path);
        var ext = Path.GetExtension(path);
        for (var i = 1; ; i++)
        {
            var candidate = Path.Combine(dir, $"{stem} ({i}){ext}");
            if (!File.Exists(candidate)) return candidate;
        }
    }

    private static string SanitizeFilename(string name)
    {
        var baseName = Path.GetFileName(name.Replace('\\', '/'));
        var safe = new string(baseName.Select(c =>
            char.IsLetterOrDigit(c) || c is '_' or '-' || (c == '.' && true) ? c : '_').ToArray());
        // Reject pure dot segments
        if (string.IsNullOrEmpty(safe) || safe is "." or ".." || safe.All(c => c == '.'))
            safe = "received_file";
        var reserved = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        };
        var stem = Path.GetFileNameWithoutExtension(safe);
        if (reserved.Contains(stem)) safe = "file_" + safe;
        return safe.Length > 200 ? safe[..200] : safe;
    }

    public async Task StopAsync()
    {
        _cts?.Cancel();
        _listener?.Stop();
        _listener = null;
        _cts?.Dispose();
        _cts = null;
        await Task.CompletedTask;
    }

    public async ValueTask DisposeAsync() => await StopAsync();
}
