using System.Diagnostics;
using System.Text.RegularExpressions;

namespace SpaceDrop.Tray.Services;

/// <summary>
/// Reads the BSSID of the PC's current Wi-Fi association via netsh.
/// Returns null when on Ethernet-only or Wi-Fi is unavailable.
/// </summary>
public static class WlanBssidReader
{
    private static readonly Regex BssidLine = new(
        @"^\s*BSSID\s*:\s*([0-9a-fA-F]{2}(?::[0-9a-fA-F]{2}){5})\s*$",
        RegexOptions.Multiline | RegexOptions.Compiled);

    public static string? TryGetConnectedBssid()
    {
        try
        {
            var psi = new ProcessStartInfo("netsh", "wlan show interfaces")
            {
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                UseShellExecute = false,
                CreateNoWindow = true
            };
            using var process = Process.Start(psi);
            if (process is null)
            {
                return null;
            }

            var output = process.StandardOutput.ReadToEnd();
            process.WaitForExit(5_000);
            if (process.ExitCode != 0 || string.IsNullOrWhiteSpace(output))
            {
                return null;
            }

            var match = BssidLine.Match(output);
            if (!match.Success)
            {
                return null;
            }

            return match.Groups[1].Value.ToLowerInvariant();
        }
        catch (Exception ex)
        {
            Console.WriteLine($"WlanBssidReader: {ex.Message}");
            return null;
        }
    }
}
