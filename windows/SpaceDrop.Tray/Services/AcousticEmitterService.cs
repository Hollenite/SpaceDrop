using NAudio.CoreAudioApi;
using NAudio.Wave;
using SpaceDrop.AcousticFsk;

namespace SpaceDrop.Tray.Services;

/// <summary>
/// Idle-by-default FSK emitter. Starts only when <see cref="Arm"/> is called (BLE/TCP),
/// runs for a bounded window, then returns to silence.
/// </summary>
public sealed class AcousticEmitterService : IAsyncDisposable
{
    private const int SampleRate = FskProtocol.SampleRate;
    private const double Amplitude = FskProtocol.Amplitude;
    private const int MaxDurationMs = 60_000;
    private const int DefaultDurationMs = 20_000;

    private readonly object _gate = new();
    private string? _deviceId;
    private CancellationTokenSource? _armCts;
    private Task? _loopTask;

    public bool IsArmed { get; private set; }

    public void Configure(string deviceId)
    {
        _deviceId = deviceId;
    }

    /// <summary>Start emitting for up to <paramref name="durationMs"/> (clamped).</summary>
    public void Arm(int durationMs = DefaultDurationMs)
    {
        if (string.IsNullOrWhiteSpace(_deviceId)) return;
        var ms = Math.Clamp(durationMs, 1_000, MaxDurationMs);

        lock (_gate)
        {
            _armCts?.Cancel();
            _armCts?.Dispose();
            _armCts = new CancellationTokenSource();
            var ct = _armCts.Token;
            IsArmed = true;
            _loopTask = Task.Run(() => RunArmedAsync(_deviceId!, ms, ct), ct);
        }

        Console.WriteLine($"Acoustic ARM {ms} ms");
    }

    public void Disarm()
    {
        lock (_gate)
        {
            _armCts?.Cancel();
            IsArmed = false;
        }
        Console.WriteLine("Acoustic DISARM");
    }

    private async Task RunArmedAsync(string deviceId, int durationMs, CancellationToken ct)
    {
        using var timeoutCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        timeoutCts.CancelAfter(durationMs);
        var linked = timeoutCts.Token;

        MMDevice device;
        try
        {
            device = SelectSpeaker();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Acoustic emitter disabled (no output device): {ex.Message}");
            IsArmed = false;
            return;
        }

        Console.WriteLine($"Acoustic emitter using output: {device.FriendlyName}");
        var encoder = new FskEncoder(SampleRate, Amplitude);

        try
        {
            while (!linked.IsCancellationRequested)
            {
                var epoch = AcousticToken.EpochFromUnixSeconds(DateTimeOffset.UtcNow.ToUnixTimeSeconds());
                var frame = encoder.EncodeFrameWithGap(deviceId, epoch, FskProtocol.InterFrameGapMs);
                var token = AcousticToken.ComputeToken20(deviceId, epoch);
                Console.WriteLine($"Acoustic emit epoch={epoch} token={AcousticToken.TokenHex(token)}");

                var provider = new FskLoopWaveProvider(frame, SampleRate);
                WasapiOut? output = null;
                try
                {
                    output = new WasapiOut(device, AudioClientShareMode.Shared, true, 50);
                    output.Init(provider);
                    output.Play();

                    while (!linked.IsCancellationRequested &&
                           AcousticToken.EpochFromUnixSeconds(DateTimeOffset.UtcNow.ToUnixTimeSeconds()) == epoch)
                    {
                        await Task.Delay(500, linked);
                    }
                }
                catch (OperationCanceledException)
                {
                    break;
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"Acoustic emit error: {ex.Message}");
                    try { await Task.Delay(1000, linked); } catch (OperationCanceledException) { break; }
                }
                finally
                {
                    try { output?.Stop(); } catch { /* ignore */ }
                    output?.Dispose();
                }
            }
        }
        finally
        {
            IsArmed = false;
            Console.WriteLine("Acoustic window ended (silent)");
        }
    }

    private static MMDevice SelectSpeaker()
    {
        using var enumerator = new MMDeviceEnumerator();
        var devices = enumerator.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active).ToList();
        if (devices.Count == 0)
        {
            throw new InvalidOperationException("No active playback devices found.");
        }

        var speaker = devices.FirstOrDefault(d => IsSpeaker(d.FriendlyName));
        if (speaker != null) return speaker;

        var notHeadphone = devices.FirstOrDefault(d => !IsHeadphoneLike(d.FriendlyName));
        return notHeadphone ?? devices[0];
    }

    private static bool IsSpeaker(string friendlyName)
    {
        var n = friendlyName.ToLowerInvariant();
        if (IsHeadphoneLike(n)) return false;
        return n.Contains("speaker") || n.Contains("realtek") || n.Contains("laptop") ||
               n.Contains("internal") || n.Contains("display audio") || n.Contains("monitor") ||
               n.Contains("nvidia") || n.Contains("intel") || n.Contains("soundbar");
    }

    private static bool IsHeadphoneLike(string friendlyName)
    {
        var n = friendlyName.ToLowerInvariant();
        return n.Contains("headphone") || n.Contains("headset") || n.Contains("earphone") ||
               n.Contains("earbud") || n.Contains("airpod") || n.Contains("hands-free") ||
               n.Contains("hands free") || n.Contains("in-ear");
    }

    public async Task StopAsync()
    {
        Disarm();
        Task? task;
        lock (_gate) { task = _loopTask; }
        if (task is not null)
        {
            try { await task; } catch (OperationCanceledException) { /* expected */ }
        }
    }

    public async ValueTask DisposeAsync()
    {
        await StopAsync();
        _armCts?.Dispose();
    }
}
