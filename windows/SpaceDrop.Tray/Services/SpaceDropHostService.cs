using System.Net.NetworkInformation;
using System.Text.Json;
using System.Windows.Forms;
using SpaceDrop.Tray.Protocol;

namespace SpaceDrop.Tray.Services;

public sealed class SpaceDropHostService : IAsyncDisposable
{
    private readonly IdentityKeyStore _identity = new();
    private readonly PairedPeerStore _peers = new();
    private readonly LocalIpSelector _ipSelector = new();
    private readonly MdnsAdvertiser _mdns = new();
    private readonly BleGattService _ble = new();
    private readonly AcousticEmitterService _acoustic = new();
    private TcpTransferServer? _tcp;
    private CancellationTokenSource? _cts;
    private string? _currentIp;
    private PairingForm? _pairingForm;

    public IdentityKeyStore Identity => _identity;
    public PairedPeerStore Peers => _peers;

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        _cts = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        _identity.LoadOrCreate();
        var hostname = Environment.MachineName;

        Console.WriteLine($"Loaded device ID: {_identity.DeviceId}");

        _acoustic.Configure(_identity.DeviceId);
        _ble.SetControlHandler(HandleBleControl);

        _tcp = new TcpTransferServer(
            _identity,
            _peers,
            hostname,
            isPairingWindowOpen: () => _pairingForm?.IsOpen == true,
            confirmPairing: ConfirmPairingOnUiThread,
            confirmReceive: ConfirmReceiveOnUiThread,
            onAcousticArm: ms => _acoustic.Arm(ms));

        await _tcp.StartAsync(_cts.Token);
        await RefreshNetworkAsync(_identity.DeviceId, hostname);

        NetworkChange.NetworkAddressChanged += OnNetworkAddressChanged;
        NetworkChange.NetworkAvailabilityChanged += OnNetworkAvailabilityChanged;
        _ = Task.Run(() => NetworkWatchLoopAsync(_identity.DeviceId, hostname, _cts.Token), _cts.Token);
    }

    public void ShowPairingWindow()
    {
        if (_pairingForm is { IsOpen: true })
        {
            _pairingForm.BringToFront();
            return;
        }

        _pairingForm = new PairingForm(_identity, _peers);
        _pairingForm.Show();
    }

    private void HandleBleControl(string json)
    {
        try
        {
            using var doc = JsonDocument.Parse(json);
            var op = doc.RootElement.GetProperty("op").GetString();
            if (op == "DISARM")
            {
                _acoustic.Disarm();
                return;
            }
            if (op == "ARM")
            {
                var ms = doc.RootElement.TryGetProperty("duration_ms", out var d) && d.TryGetInt32(out var v)
                    ? v : SpaceDropProtocol.DefaultAcousticArmMs;
                _acoustic.Arm(ms);
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"BLE control parse error: {ex.Message}");
        }
    }

    private bool ConfirmPairingOnUiThread(string phoneName, string deviceId)
    {
        bool result = false;
        void Show()
        {
            result = MessageBox.Show(
                $"Allow pairing with \"{phoneName}\"?\n\nDevice ID: {deviceId}",
                "SpaceDrop pairing",
                MessageBoxButtons.YesNo,
                MessageBoxIcon.Question) == DialogResult.Yes;
        }

        if (Application.OpenForms.Count > 0)
        {
            var form = Application.OpenForms[0]!;
            form.Invoke(Show);
        }
        else
        {
            Show();
        }

        if (result) _pairingForm?.NotifyPeersChanged();
        return result;
    }

    private bool ConfirmReceiveOnUiThread(string phoneName, string fileName, long size)
    {
        bool result = false;
        void Show()
        {
            result = MessageBox.Show(
                $"Accept file from {phoneName}?\n\n{fileName}\n{size} bytes",
                "SpaceDrop receive",
                MessageBoxButtons.YesNo,
                MessageBoxIcon.Question) == DialogResult.Yes;
        }

        if (Application.OpenForms.Count > 0)
        {
            Application.OpenForms[0]!.Invoke(Show);
        }
        else Show();
        return result;
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
            catch (OperationCanceledException) { break; }
        }
    }

    private async Task RefreshNetworkSafeAsync()
    {
        if (_tcp is null) return;
        try
        {
            await RefreshNetworkAsync(_identity.DeviceId, Environment.MachineName);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"Network refresh failed: {ex.Message}");
        }
    }

    private async Task RefreshNetworkAsync(string deviceId, string hostname)
    {
        var selection = _ipSelector.SelectBestIp();
        if (selection.Ip == _currentIp) return;

        _currentIp = selection.Ip;
        var bssid = WlanBssidReader.TryGetConnectedBssid();
        Console.WriteLine($"Using {selection.Ip} for mDNS ({selection.Reason})");

        await _mdns.StartAsync(
            hostname,
            deviceId,
            selection.Ip,
            SpaceDropProtocol.Port,
            bssid,
            _identity.FingerprintHex,
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
        var acoustic = _acoustic.IsArmed ? "tone armed" : "tone silent";
        var pairs = _peers.List().Count;
        return $"SpaceDrop — {_currentIp ?? "no IP"} — {ble} — {acoustic} — {pairs} paired";
    }

    public string ReceivedDirectory => _tcp?.ReceivedDirectory
        ?? ReceivedPathResolver.Resolve();

    public async Task StopAsync()
    {
        NetworkChange.NetworkAddressChanged -= OnNetworkAddressChanged;
        NetworkChange.NetworkAvailabilityChanged -= OnNetworkAvailabilityChanged;
        _cts?.Cancel();
        _pairingForm?.Close();
        await _acoustic.StopAsync();
        await _mdns.StopAsync();
        await _ble.StopAsync();
        if (_tcp is not null) await _tcp.StopAsync();
    }

    public async ValueTask DisposeAsync()
    {
        await StopAsync();
        _cts?.Dispose();
        await _acoustic.DisposeAsync();
        await _mdns.DisposeAsync();
        await _ble.DisposeAsync();
        if (_tcp is not null) await _tcp.DisposeAsync();
    }
}
