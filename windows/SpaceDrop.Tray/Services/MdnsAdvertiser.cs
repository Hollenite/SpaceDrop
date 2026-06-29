using System.Net;
using Makaretu.Dns;
using SpaceDrop.Tray.Protocol;

namespace SpaceDrop.Tray.Services;

public sealed class MdnsAdvertiser : IAsyncDisposable
{
    private MulticastService? _multicastService;
    private ServiceDiscovery? _serviceDiscovery;
    private ServiceProfile? _profile;

    public Task StartAsync(string hostname, string deviceId, string ip, int port, CancellationToken cancellationToken)
    {
        _ = cancellationToken;
        StopAsync().GetAwaiter().GetResult();

        _multicastService = new MulticastService();
        _multicastService.Start();

        _serviceDiscovery = new ServiceDiscovery(_multicastService);

        _profile = new ServiceProfile(
            hostname,
            "_spacedrop._tcp",
            (ushort)port,
            new[] { IPAddress.Parse(ip) });
        _profile.AddProperty("device_id", deviceId);
        _profile.AddProperty("version", "1");

        _serviceDiscovery.Advertise(_profile);

        Console.WriteLine(
            $"Registered mDNS service '{hostname}._spacedrop._tcp.local.' at {ip}:{port}");
        return Task.CompletedTask;
    }

    public Task StopAsync()
    {
        if (_serviceDiscovery is not null && _profile is not null)
        {
            _serviceDiscovery.Unadvertise(_profile);
        }

        _profile = null;
        _serviceDiscovery = null;

        _multicastService?.Stop();
        _multicastService = null;

        return Task.CompletedTask;
    }

    public async ValueTask DisposeAsync()
    {
        await StopAsync();
    }
}
