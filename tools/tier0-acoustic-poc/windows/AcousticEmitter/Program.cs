using NAudio.CoreAudioApi;
using NAudio.Wave;
using NAudio.Wave.SampleProviders;

namespace SpaceDrop.AcousticEmitter;

internal static class Program
{
    private const double SweepMinHz = 17_000;
    private const double SweepMaxHz = 21_000;
    private const double SweepStepHz = 500;
    private const double SweepDwellSec = 5;
    private const double BurstOnSec = 1;
    private const double BurstOffSec = 2;

    public static int Main(string[] args)
    {
        var options = EmitterOptions.Parse(args);
        if (options.ShowHelp)
        {
            PrintHelp();
            return 0;
        }

        if (options.ListDevices)
        {
            AudioOutputSelector.PrintDeviceList();
            return 0;
        }

        var outputDevice = AudioOutputSelector.Resolve(options);

        Console.WriteLine("SpaceDrop Acoustic POC — Windows emitter");
        Console.WriteLine($"  output={AudioOutputSelector.DescribeDevice(outputDevice)}");
        Console.WriteLine($"  mode={options.Mode} freq={options.FrequencyHz:F0} Hz duration={options.DurationSec}s amplitude={options.Amplitude:F2} fs={options.SampleRate}");
        Console.WriteLine("Press Ctrl+C to stop early.\n");

        using var cts = new CancellationTokenSource();
        Console.CancelKeyPress += (_, e) =>
        {
            e.Cancel = true;
            cts.Cancel();
        };

        try
        {
            return options.Mode switch
            {
                EmitterMode.Continuous => RunContinuous(options, outputDevice, cts.Token),
                EmitterMode.Burst => RunBurst(options, outputDevice, cts.Token),
                EmitterMode.Sweep => RunSweep(options, outputDevice, cts.Token),
                _ => 1
            };
        }
        catch (OperationCanceledException)
        {
            Console.WriteLine("\nStopped.");
            return 0;
        }
    }

    private static int RunContinuous(EmitterOptions options, MMDevice outputDevice, CancellationToken cancellationToken)
    {
        using var playback = CreatePlayback(options.FrequencyHz, options.Amplitude, options.SampleRate, outputDevice);
        MaybeWriteWavReference(options, options.FrequencyHz, options.DurationSec);

        playback.Play();
        WaitForDuration(options.DurationSec, cancellationToken, () =>
            LogLevel(options.FrequencyHz, options.Amplitude, on: true));

        playback.Stop();
        return 0;
    }

    private static int RunBurst(EmitterOptions options, MMDevice outputDevice, CancellationToken cancellationToken)
    {
        var endAt = options.DurationSec <= 0
            ? DateTime.MaxValue
            : DateTime.UtcNow.AddSeconds(options.DurationSec);

        using var playback = CreatePlayback(options.FrequencyHz, options.Amplitude, options.SampleRate, outputDevice);

        while (DateTime.UtcNow < endAt && !cancellationToken.IsCancellationRequested)
        {
            playback.Play();
            LogLevel(options.FrequencyHz, options.Amplitude, on: true);
            SleepSeconds(BurstOnSec, cancellationToken);

            playback.Stop();
            LogLevel(options.FrequencyHz, options.Amplitude, on: false);
            SleepSeconds(BurstOffSec, cancellationToken);
        }

        playback.Stop();
        return 0;
    }

    private static int RunSweep(EmitterOptions options, MMDevice outputDevice, CancellationToken cancellationToken)
    {
        var frequencies = new List<double>();
        for (var f = SweepMinHz; f <= SweepMaxHz + 0.1; f += SweepStepHz)
        {
            frequencies.Add(f);
        }

        var endAt = options.DurationSec <= 0
            ? DateTime.MaxValue
            : DateTime.UtcNow.AddSeconds(options.DurationSec);

        foreach (var freq in frequencies)
        {
            if (DateTime.UtcNow >= endAt || cancellationToken.IsCancellationRequested)
            {
                break;
            }

            Console.WriteLine($"Sweep step: {freq:F0} Hz ({SweepDwellSec}s)");
            using var playback = CreatePlayback(freq, options.Amplitude, options.SampleRate, outputDevice);
            playback.Play();
            SleepSeconds(SweepDwellSec, cancellationToken);
            playback.Stop();
        }

        return 0;
    }

    private static WasapiOut CreatePlayback(double frequencyHz, double amplitude, int sampleRate, MMDevice outputDevice)
    {
        var generator = new SignalGenerator(sampleRate, 1)
        {
            Type = SignalGeneratorType.Sin,
            Frequency = frequencyHz,
            Gain = amplitude
        };

        var wasapiOut = new WasapiOut(outputDevice, AudioClientShareMode.Shared, true, 50);
        wasapiOut.Init(generator);
        return wasapiOut;
    }

    private static void MaybeWriteWavReference(EmitterOptions options, double frequencyHz, int durationSec)
    {
        if (string.IsNullOrWhiteSpace(options.WavPath))
        {
            return;
        }

        var seconds = durationSec <= 0 ? 10 : Math.Min(durationSec, 30);
        var generator = new SignalGenerator(options.SampleRate, 1)
        {
            Type = SignalGeneratorType.Sin,
            Frequency = frequencyHz,
            Gain = options.Amplitude
        };

        var reader = new SampleToWaveProvider16(generator);
        using var writer = new WaveFileWriter(options.WavPath!, reader.WaveFormat);
        var buffer = new byte[reader.WaveFormat.AverageBytesPerSecond];
        var maxBytes = (long)reader.WaveFormat.AverageBytesPerSecond * seconds;
        long total = 0;
        while (total < maxBytes)
        {
            var read = reader.Read(buffer, 0, buffer.Length);
            if (read <= 0) break;
            writer.Write(buffer, 0, read);
            total += read;
        }

        Console.WriteLine($"Reference WAV saved: {options.WavPath} ({seconds}s @ {frequencyHz:F0} Hz)");
    }

    private static void WaitForDuration(int durationSec, CancellationToken cancellationToken, Action? onTick = null)
    {
        if (durationSec <= 0)
        {
            onTick?.Invoke();
            try
            {
                Task.Delay(Timeout.Infinite, cancellationToken).Wait(cancellationToken);
            }
            catch (OperationCanceledException)
            {
                // expected
            }
            return;
        }

        var end = DateTime.UtcNow.AddSeconds(durationSec);
        while (DateTime.UtcNow < end && !cancellationToken.IsCancellationRequested)
        {
            onTick?.Invoke();
            Thread.Sleep(500);
        }
    }

    private static void SleepSeconds(double seconds, CancellationToken cancellationToken)
    {
        Task.Delay(TimeSpan.FromSeconds(seconds), cancellationToken).Wait(cancellationToken);
    }

    private static void LogLevel(double frequencyHz, double amplitude, bool on)
    {
        var state = on ? "ON " : "OFF";
        Console.WriteLine($"[{DateTime.Now:HH:mm:ss}] {state} {frequencyHz:F0} Hz gain={amplitude:F2}");
    }

    private static void PrintHelp()
    {
        Console.WriteLine("""
            SpaceDrop Acoustic POC — Windows emitter

            Usage:
              dotnet run -- [options]

            Options:
              --freq <Hz>           Target frequency (default 19000)
              --mode <name>         continuous | burst | sweep (default continuous)
              --duration <sec>      Run time (default 60; 0 = until Ctrl+C)
              --amplitude <0-1>     Peak gain (default 0.4)
              --sample-rate <Hz>    44100 or 48000 (default 44100)
              --wav <path>          Save synthesized WAV reference (continuous mode)
              --list-devices        List playback devices and exit
              --device <index>      Force device index from --list-devices
              --device-name <text>  Force device whose name contains text (e.g. Speakers)
              --windows-default     Use Windows default device (may be headphones)
              --help                Show this help

            By default the emitter skips headphone/headset/earphone outputs and
            prefers laptop speakers (Realtek, internal, etc.).
            """);
    }
}

internal enum EmitterMode
{
    Continuous,
    Burst,
    Sweep
}

internal sealed class EmitterOptions
{
    public double FrequencyHz { get; set; } = 19_000;
    public EmitterMode Mode { get; set; } = EmitterMode.Continuous;
    public int DurationSec { get; set; } = 60;
    public double Amplitude { get; set; } = 0.4;
    public int SampleRate { get; set; } = 44100;
    public string? WavPath { get; set; }
    public bool ShowHelp { get; set; }
    public bool ListDevices { get; set; }
    public int? DeviceIndex { get; set; }
    public string? DeviceName { get; set; }
    public bool UseWindowsDefault { get; set; }

    public static EmitterOptions Parse(string[] args)
    {
        var options = new EmitterOptions();
        for (var i = 0; i < args.Length; i++)
        {
            var arg = args[i];
            switch (arg.ToLowerInvariant())
            {
                case "--help":
                case "-h":
                    options.ShowHelp = true;
                    return options;
                case "--list-devices":
                    options.ListDevices = true;
                    break;
                case "--device":
                    options.DeviceIndex = ParseInt(NextArg(args, ref i), "--device");
                    break;
                case "--device-name":
                    options.DeviceName = NextArg(args, ref i);
                    break;
                case "--windows-default":
                    options.UseWindowsDefault = true;
                    break;
                case "--freq":
                    options.FrequencyHz = ParseDouble(NextArg(args, ref i), "--freq");
                    break;
                case "--mode":
                    options.Mode = ParseMode(NextArg(args, ref i));
                    break;
                case "--duration":
                    options.DurationSec = ParseInt(NextArg(args, ref i), "--duration");
                    break;
                case "--amplitude":
                    options.Amplitude = ParseDouble(NextArg(args, ref i), "--amplitude");
                    break;
                case "--sample-rate":
                    options.SampleRate = ParseInt(NextArg(args, ref i), "--sample-rate");
                    break;
                case "--wav":
                    options.WavPath = NextArg(args, ref i);
                    break;
                default:
                    throw new ArgumentException($"Unknown argument: {arg}");
            }
        }

        if (options.Amplitude is <= 0 or > 1)
        {
            throw new ArgumentException("--amplitude must be between 0 and 1");
        }

        if (options.SampleRate is not (44_100 or 48_000))
        {
            throw new ArgumentException("--sample-rate must be 44100 or 48000");
        }

        return options;
    }

    private static string NextArg(string[] args, ref int i)
    {
        if (i + 1 >= args.Length)
        {
            throw new ArgumentException($"Missing value after {args[i]}");
        }
        i++;
        return args[i];
    }

    private static double ParseDouble(string value, string flag) =>
        double.TryParse(value, out var d) ? d : throw new ArgumentException($"Invalid {flag}: {value}");

    private static int ParseInt(string value, string flag) =>
        int.TryParse(value, out var n) ? n : throw new ArgumentException($"Invalid {flag}: {value}");

    private static EmitterMode ParseMode(string value) => value.ToLowerInvariant() switch
    {
        "continuous" => EmitterMode.Continuous,
        "burst" => EmitterMode.Burst,
        "sweep" => EmitterMode.Sweep,
        _ => throw new ArgumentException($"Unknown mode: {value}. Use continuous, burst, or sweep.")
    };
}
