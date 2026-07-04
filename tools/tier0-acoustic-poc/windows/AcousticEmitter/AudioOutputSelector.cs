using NAudio.CoreAudioApi;
using NAudio.Wave;

namespace SpaceDrop.AcousticEmitter;

internal static class AudioOutputSelector
{
    /// <summary>
    /// Pick a render endpoint. Prefers built-in / external speakers over headphones.
    /// </summary>
    public static MMDevice Resolve(EmitterOptions options)
    {
        var devices = ListActiveRenderDevices();

        if (options.DeviceIndex.HasValue)
        {
            ValidateIndex(devices, options.DeviceIndex.Value);
            return devices[options.DeviceIndex.Value];
        }

        if (!string.IsNullOrWhiteSpace(options.DeviceName))
        {
            var match = devices.FirstOrDefault(d =>
                d.FriendlyName.Contains(options.DeviceName, StringComparison.OrdinalIgnoreCase));
            if (match == null)
            {
                throw new ArgumentException($"No output device matching \"{options.DeviceName}\".");
            }
            return match;
        }

        if (options.UseWindowsDefault)
        {
            return new MMDeviceEnumerator().GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
        }

        return PreferSpeakers(devices);
    }

    public static string DescribeDevice(MMDevice device) =>
        $"{device.FriendlyName} ({Classify(device.FriendlyName)})";

    public static void PrintDeviceList()
    {
        var devices = ListActiveRenderDevices();
        Console.WriteLine("Available playback devices (WASAPI):");
        for (var i = 0; i < devices.Count; i++)
        {
            var name = devices[i].FriendlyName;
            Console.WriteLine($"  [{i}] {name}  ({Classify(name)})");
        }
        Console.WriteLine();
        Console.WriteLine("Default auto-select skips headphone/headset/earphone devices.");
        Console.WriteLine("Override with --device <index> or --device-name \"Speakers\".");
    }

    private static List<MMDevice> ListActiveRenderDevices()
    {
        var enumerator = new MMDeviceEnumerator();
        return enumerator
            .EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active)
            .ToList();
    }

    private static MMDevice PreferSpeakers(IReadOnlyList<MMDevice> devices)
    {
        if (devices.Count == 0)
        {
            throw new InvalidOperationException("No active playback devices found.");
        }

        var speaker = devices.FirstOrDefault(d => Classify(d.FriendlyName) == DeviceKind.Speaker);
        if (speaker != null)
        {
            return speaker;
        }

        var notHeadphone = devices.FirstOrDefault(d => Classify(d.FriendlyName) != DeviceKind.Headphone);
        if (notHeadphone != null)
        {
            return notHeadphone;
        }

        Console.WriteLine("Warning: all outputs look like headphones; using first device. Use --device to override.");
        return devices[0];
    }

    private static void ValidateIndex(IReadOnlyList<MMDevice> devices, int index)
    {
        if (index < 0 || index >= devices.Count)
        {
            throw new ArgumentException($"Device index {index} out of range (0..{devices.Count - 1}). Run --list-devices.");
        }
    }

    private static DeviceKind Classify(string friendlyName)
    {
        var n = friendlyName.ToLowerInvariant();
        if (IsHeadphoneLike(n))
        {
            return DeviceKind.Headphone;
        }
        if (n.Contains("speaker") || n.Contains("realtek") || n.Contains("laptop") ||
            n.Contains("internal") || n.Contains("display audio") || n.Contains("monitor") ||
            n.Contains("nvidia") || n.Contains("intel") || n.Contains("soundbar"))
        {
            return DeviceKind.Speaker;
        }
        return DeviceKind.Unknown;
    }

    private static bool IsHeadphoneLike(string lowerName) =>
        lowerName.Contains("headphone") ||
        lowerName.Contains("headset") ||
        lowerName.Contains("earphone") ||
        lowerName.Contains("earbud") ||
        lowerName.Contains("airpod") ||
        lowerName.Contains("hands-free") ||
        lowerName.Contains("hands free") ||
        lowerName.Contains("in-ear") ||
        (lowerName.Contains("usb") && lowerName.Contains("audio") && lowerName.Contains("head"));

    private enum DeviceKind
    {
        Speaker,
        Headphone,
        Unknown
    }
}
