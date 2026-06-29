using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace SpaceDrop.Tray.Services;

public sealed class LocalIpSelector
{
    public sealed record Selection(string Ip, string Reason);

    public Selection SelectBestIp()
    {
        var candidates = CollectIpv4Candidates();
        if (candidates.Count == 0)
        {
            throw new InvalidOperationException("Could not determine a local IPv4 address");
        }

        var defaultRouteIp = GetDefaultRouteIp();
        var usable = candidates.Where(ip => !IsUnusableForMdns(ip)).ToList();
        if (usable.Count == 0)
        {
            throw new InvalidOperationException("No usable local IPv4 address found");
        }

        if (defaultRouteIp is not null && usable.Contains(defaultRouteIp))
        {
            return new Selection(defaultRouteIp, "default_route");
        }

        var chosen = usable.MinBy(IpPreferenceScore)!;
        return new Selection(chosen, "scored_fallback");
    }

    private static string? GetDefaultRouteIp()
    {
        using var probe = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
        try
        {
            probe.Connect("8.8.8.8", 80);
            return ((IPEndPoint)probe.LocalEndPoint!).Address.ToString();
        }
        catch (SocketException)
        {
            return null;
        }
    }

    private static List<string> CollectIpv4Candidates()
    {
        var candidates = new List<string>();

        try
        {
            var hostName = Dns.GetHostName();
            foreach (var address in Dns.GetHostAddresses(hostName))
            {
                if (address.AddressFamily == AddressFamily.InterNetwork)
                {
                    var ip = address.ToString();
                    if (!candidates.Contains(ip))
                    {
                        candidates.Add(ip);
                    }
                }
            }
        }
        catch (SocketException)
        {
            // ignore
        }

        var defaultRouteIp = GetDefaultRouteIp();
        if (defaultRouteIp is not null && !candidates.Contains(defaultRouteIp))
        {
            candidates.Add(defaultRouteIp);
        }

        foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (nic.OperationalStatus != OperationalStatus.Up) continue;
            foreach (var address in nic.GetIPProperties().UnicastAddresses)
            {
                if (address.Address.AddressFamily != AddressFamily.InterNetwork) continue;
                var ip = address.Address.ToString();
                if (!candidates.Contains(ip))
                {
                    candidates.Add(ip);
                }
            }
        }

        return candidates;
    }

    private static bool IsUnusableForMdns(string ip) =>
        ip.StartsWith("127.") || ip.StartsWith("169.254.");

    private static bool IsWindowsIcsHotspot(string ip) =>
        ip == "192.168.137.1" || ip.StartsWith("192.168.137.");

    private static int IpPreferenceScore(string ip)
    {
        var parts = ip.Split('.');
        if (parts.Length != 4) return 100;
        if (IsUnusableForMdns(ip)) return 200;
        if (IsWindowsIcsHotspot(ip)) return 180;

        var first = int.Parse(parts[0]);
        var second = int.Parse(parts[1]);
        if (first == 192 && second == 168) return 0;
        if (first == 10) return 1;
        if (first == 172 && second is >= 16 and <= 31) return 2;
        if (first == 100 && second is >= 64 and <= 127) return 10;
        return 50;
    }
}
