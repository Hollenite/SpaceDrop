using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using SpaceDrop.Tray.Protocol;

namespace SpaceDrop.Tray.Services;

public sealed class TcpTransferServer : IAsyncDisposable
{
    private readonly string _deviceId;
    private readonly string _hostname;
    private readonly string _receivedDir;
    private TcpListener? _listener;
    private CancellationTokenSource? _cts;

    public string ReceivedDirectory { get; }

    public TcpTransferServer(string deviceId, string hostname)
    {
        _deviceId = deviceId;
        _hostname = hostname;
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
        Console.WriteLine($"Listening on port {SpaceDropProtocol.Port}");
        Console.WriteLine(
            "If manual connect fails instantly, allow inbound TCP port " +
            $"{SpaceDropProtocol.Port} through Windows Firewall.");

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
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Accept error: {ex.Message}");
            }
        }
    }

    private async Task HandleConnectionAsync(TcpClient client, string peerIp, CancellationToken cancellationToken)
    {
        using var _ = client;
        Console.WriteLine($"Connection attempt from {peerIp}");

        try
        {
            await using var stream = client.GetStream();
            // Control lines are read byte-by-byte off the raw stream (not via StreamReader) so the
            // reader can never buffer ahead into the following file's raw bytes — important now that a
            // single connection carries multiple FILE_META -> bytes -> FILE_ACK cycles.
            await using var writer = new StreamWriter(stream, Encoding.UTF8, leaveOpen: true) { AutoFlush = true };

            var line = await ReadLineAsync(stream, cancellationToken);
            if (string.IsNullOrWhiteSpace(line))
            {
                Console.WriteLine($"Empty request from {peerIp}");
                return;
            }

            using var pingDoc = JsonDocument.Parse(line);
            var ping = pingDoc.RootElement;
            if (ping.GetProperty("type").GetString() != "PING")
            {
                Console.WriteLine($"Unexpected message type from {peerIp}");
                return;
            }

            if (ping.GetProperty("token").GetString() != SpaceDropProtocol.SharedToken)
            {
                Console.WriteLine($"Warning: rejected connection from {peerIp} — invalid token");
                return;
            }

            var peerName = ping.TryGetProperty("device_name", out var nameEl) ? nameEl.GetString() : "unknown";
            var peerId = ping.TryGetProperty("device_id", out var idEl) ? idEl.GetString() : "unknown";
            var expectedCount = ping.TryGetProperty("count", out var countEl) && countEl.TryGetInt32(out var c) ? c : 1;

            var pong = new
            {
                type = "PONG",
                device_name = _hostname,
                device_id = _deviceId,
                token = SpaceDropProtocol.SharedToken
            };
            await writer.WriteLineAsync(JsonSerializer.Serialize(pong));
            Console.WriteLine($"Connected to {peerName} ({peerId}) — expecting {expectedCount} file(s)");

            var received = 0;
            while (true)
            {
                var nextLine = await ReadLineAsync(stream, cancellationToken);
                if (string.IsNullOrWhiteSpace(nextLine)) break;

                using var nextDoc = JsonDocument.Parse(nextLine);
                var next = nextDoc.RootElement;
                var msgType = next.GetProperty("type").GetString();
                if (msgType == "FILE_META")
                {
                    await HandleFileMetaAsync(next, stream, writer, peerIp, received + 1, expectedCount);
                    received++;
                    if (received >= expectedCount) break;
                }
                else
                {
                    Console.WriteLine($"Unexpected message type from {peerIp}: {msgType}");
                    break;
                }
            }

            Console.WriteLine($"Connection from {peerName} complete — received {received}/{expectedCount} file(s)");
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Connection error from {peerIp}: {ex.Message}");
        }
    }

    /// <summary>Reads a single '\n'-terminated UTF-8 line off the raw stream without over-buffering.</summary>
    private static async Task<string?> ReadLineAsync(NetworkStream stream, CancellationToken cancellationToken)
    {
        var bytes = new List<byte>(128);
        var one = new byte[1];
        while (true)
        {
            var read = await stream.ReadAsync(one.AsMemory(0, 1), cancellationToken);
            if (read == 0)
            {
                return bytes.Count == 0 ? null : Encoding.UTF8.GetString(bytes.ToArray());
            }
            if (one[0] == (byte)'\n') break;
            if (one[0] == (byte)'\r') continue;
            bytes.Add(one[0]);
        }
        return Encoding.UTF8.GetString(bytes.ToArray());
    }

    private async Task HandleFileMetaAsync(
        JsonElement message,
        NetworkStream stream,
        StreamWriter writer,
        string peerIp,
        int fileIndex,
        int fileCount)
    {
        if (message.GetProperty("token").GetString() != SpaceDropProtocol.SharedToken)
        {
            await writer.WriteLineAsync(JsonSerializer.Serialize(new
            {
                type = "FILE_ACK",
                ok = false,
                error = "invalid token"
            }));
            Console.WriteLine($"Rejected FILE_META from {peerIp} — invalid token");
            return;
        }

        var name = SanitizeFilename(message.GetProperty("name").GetString() ?? "received_file");
        var size = message.GetProperty("size").GetInt64();
        if (size < 0)
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
                if (read == 0)
                {
                    throw new EndOfStreamException($"Expected {size} bytes");
                }
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
            // #region agent log
            DebugAgentLog.Write("H5", "TcpTransferServer.cs:HandleFileMeta", "File saved", new
            {
                dest,
                size,
                peerIp,
                runId = "transfer-check"
            });
            // #endregion
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

    /// <summary>Avoids clobbering an existing file (or an earlier file in the same batch).</summary>
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
        var safe = new string(name.Select(c =>
            char.IsLetterOrDigit(c) || c is '.' or '_' or '-' ? c : '_').ToArray());
        if (string.IsNullOrEmpty(safe)) safe = "received_file";
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

    public async ValueTask DisposeAsync()
    {
        await StopAsync();
    }
}
