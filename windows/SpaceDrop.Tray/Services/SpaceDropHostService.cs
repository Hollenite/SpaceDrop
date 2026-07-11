using System.Net.NetworkInformation;
using SpaceDrop.Tray.Protocol;

namespace SpaceDrop.Tray.Services;

public sealed class SpaceDropHostService : IAsyncDisposable
{
    private readonly DeviceIdStore _deviceIdStore = new();
    private readonly LocalIpSelector _ipSelector = new();
    private readonly MdnsAdvertiser _mdns = new();
    private readonly BleGattService _ble = new();
    private readonly AcousticEmitterService _acoustic = new();
    private TcpTransferServer? _tcp;
    private CancellationTokenSource? _cts;
    private string? _currentIp;

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        _cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        var deviceId = _deviceIdStore.GetOrCreateDeviceId();
        var hostname = Environment.MachineName;

        Console.WriteLine($"Loaded device ID: {deviceId}");

        _tcp = new TcpTransferServer(deviceId, hostname);
        await _tcp.StartAsync(_cts.Token);

        // Continuously emit the 19 kHz FSK device-id token for acoustic corroboration on the phone.
        _acoustic.Start(deviceId, _cts.Token);

        await RefreshNetworkAsync(deviceId, hostname);

        NetworkChange.NetworkAddressChanged += OnNetworkAddressChanged;
        NetworkChange.NetworkAvailabilityChanged += OnNetworkAvailabilityChanged;

        _ = Task.Run(() => NetworkWatchLoopAsync(deviceId, hostname, _cts.Token), _cts.Token);
    }

    private void OnNetworkAddressChanged(object? sender, EventArgs e) =>
        _ = RefreshNetworkSafeAsync();

    private void OnNetworkAvailabilityChanged(object? sender, NetworkAvailabilityEventArgs e) =>
        _ = RefreshNetworkSafeAsync();

    private async Task NetworkWatchLoopAsync(string deviceId, string hostname, CancellationToken cancellationToken)
    {
        while (!cancellationToken.IsCancellationRequested)
        {
            try
            {
                await Task.Delay(TimeSpan.FromSeconds(30), cancellationToken);
                await RefreshNetworkAsync(deviceId, hostname);
            }
            catch (OperationCanceledException)
            {
                break;
            }
        }
    }

    private async Task RefreshNetworkSafeAsync()
    {
        if (_tcp is null) return;
        try
        {
            var deviceId = _deviceIdStore.GetOrCreateDeviceId();
            await RefreshNetworkAsync(deviceId, Environment.MachineName);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Network refresh failed: {ex.Message}");
        }
    }

    private async Task RefreshNetworkAsync(string deviceId, string hostname)
    {
        var selection = _ipSelector.SelectBestIp();
        if (selection.Ip == _currentIp)
        {
            return;
        }

        _currentIp = selection.Ip;
        var bssid = WlanBssidReader.TryGetConnectedBssid();
        Console.WriteLine($"Using {selection.Ip} for mDNS ({selection.Reason})");

        await _mdns.StartAsync(
            hostname,
            deviceId,
            selection.Ip,
            SpaceDropProtocol.Port,
            bssid,
            CancellationToken.None);

        if (_ble.IsAvailable)
        {
            await _ble.UpdateEndpointAsync(selection.Ip, SpaceDropProtocol.Port);
        }
        else
        {
            await _ble.StartAsync(deviceId, hostname, selection.Ip, SpaceDropProtocol.Port);
        }
    }

    public TrayStatus GetTrayStatus()
    {
        if (_ble.IsAvailable) return TrayStatus.Running;
        return TrayStatus.BleUnavailable;
    }

    public string GetStatusText()
    {
        var ble = _ble.IsAvailable ? "BLE on" : $"BLE off ({_ble.UnavailableReason ?? "unknown"})";
        return $"SpaceDrop — {_currentIp ?? "no IP"} — {ble}";
    }

    public string ReceivedDirectory => _tcp?.ReceivedDirectory
        ?? ReceivedPathResolver.Resolve();

    public async Task StopAsync()
    {
        NetworkChange.NetworkAddressChanged -= OnNetworkAddressChanged;
        NetworkChange.NetworkAvailabilityChanged -= OnNetworkAvailabilityChanged;
        _cts?.Cancel();

        await _acoustic.StopAsync();
        await _mdns.StopAsync();
        await _ble.StopAsync();
        if (_tcp is not null)
        {
            await _tcp.StopAsync();
        }
    }

    public async ValueTask DisposeAsync()
    {
        await StopAsync();
        _cts?.Dispose();
        await _acoustic.DisposeAsync();
        await _mdns.DisposeAsync();
        await _ble.DisposeAsync();
        if (_tcp is not null)
        {
            await _tcp.DisposeAsync();
        }
    }
}
