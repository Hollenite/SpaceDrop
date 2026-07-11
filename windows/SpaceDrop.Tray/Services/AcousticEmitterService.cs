using NAudio.CoreAudioApi;
using NAudio.Wave;
using SpaceDrop.AcousticFsk;

namespace SpaceDrop.Tray.Services;

/// <summary>
/// Background service that continuously emits this PC's device-id token as a 19 kHz FSK loop so a
/// nearby Android phone can acoustically corroborate a device already found over BLE/mDNS.
///
/// The token is derived from the tray <see cref="DeviceIdStore"/> device id and the current wall-clock
/// epoch (30 s buckets). The loop is rebuilt whenever the epoch rolls over so the token the phone
/// computes for the current epoch (+/- 1) always matches. Prefers laptop/desktop speakers over
/// headphones (same policy as the Tier 0 AcousticEmitter POC).
/// </summary>
public sealed class AcousticEmitterService : IAsyncDisposable
{
    private const int SampleRate = FskProtocol.SampleRate;
    private const double Amplitude = FskProtocol.Amplitude;

    private CancellationTokenSource? _cts;
    private Task? _loopTask;

    public void Start(string deviceId, CancellationToken cancellationToken)
    {
        if (_loopTask is not null) return;
        if (string.IsNullOrWhiteSpace(deviceId)) return;

        _cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        _loopTask = Task.Run(() => RunLoopAsync(deviceId, _cts.Token), _cts.Token);
    }

    private static async Task RunLoopAsync(string deviceId, CancellationToken ct)
    {
        MMDevice device;
        try
        {
            device = SelectSpeaker();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Acoustic emitter disabled (no output device): {ex.Message}");
            return;
        }

        Console.WriteLine($"Acoustic emitter using output: {device.FriendlyName}");
        var encoder = new FskEncoder(SampleRate, Amplitude);

        while (!ct.IsCancellationRequested)
        {
            var epoch = AcousticToken.EpochFromUnixSeconds(DateTimeOffset.UtcNow.ToUnixTimeSeconds());
            var frame = encoder.EncodeFrameWithGap(deviceId, epoch, FskProtocol.InterFrameGapMs);
            var token = AcousticToken.ComputeToken20(deviceId, epoch);
            Console.WriteLine($"Acoustic emit epoch={epoch} token={AcousticToken.TokenHex(token)} (loop)");

            var provider = new FskLoopWaveProvider(frame, SampleRate);
            WasapiOut? output = null;
            try
            {
                output = new WasapiOut(device, AudioClientShareMode.Shared, true, 50);
                output.Init(provider);
                output.Play();

                // Keep looping the current-epoch frame until the epoch rolls over, then rebuild.
                while (!ct.IsCancellationRequested &&
                       AcousticToken.EpochFromUnixSeconds(DateTimeOffset.UtcNow.ToUnixTimeSeconds()) == epoch)
                {
                    await Task.Delay(500, ct);
                }
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Acoustic emit error: {ex.Message}");
                try { await Task.Delay(2000, ct); } catch (OperationCanceledException) { break; }
            }
            finally
            {
                try { output?.Stop(); } catch { /* ignore */ }
                output?.Dispose();
            }
        }
    }

    /// <summary>Prefer built-in / external speakers over headphones (mirrors the POC selector).</summary>
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
        _cts?.Cancel();
        if (_loopTask is not null)
        {
            try { await _loopTask; }
            catch (OperationCanceledException) { /* expected */ }
            _loopTask = null;
        }
    }

    public async ValueTask DisposeAsync()
    {
        await StopAsync();
        _cts?.Dispose();
    }
}
