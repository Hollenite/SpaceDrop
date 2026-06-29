using System.Windows.Forms;
using SpaceDrop.Tray.Services;

namespace SpaceDrop.Tray;

internal sealed class TrayApplicationContext : ApplicationContext
{
    private readonly SpaceDropHostService _hostService = new();
    private readonly TrayIconService _trayIcon = new();
    private readonly System.Windows.Forms.Timer _statusTimer = new() { Interval = 5000 };
    private readonly CancellationTokenSource _cts = new();

    public TrayApplicationContext()
    {
        _trayIcon.ExitRequested += OnExitRequested;
        _statusTimer.Tick += (_, _) => UpdateTrayStatus();
        _ = StartAsync();
    }

    private async Task StartAsync()
    {
        try
        {
            await _hostService.StartAsync(_cts.Token);
            UpdateTrayStatus();
            _statusTimer.Start();
        }
        catch (Exception ex)
        {
            _trayIcon.SetStatus(TrayStatus.Error, $"SpaceDrop error: {ex.Message}");
            Console.WriteLine(ex);
        }
    }

    private void UpdateTrayStatus()
    {
        _trayIcon.SetStatus(_hostService.GetTrayStatus(), _hostService.GetStatusText());
    }

    private async void OnExitRequested()
    {
        _statusTimer.Stop();
        _cts.Cancel();
        await _hostService.DisposeAsync();
        _trayIcon.Dispose();
        ExitThread();
    }
}
