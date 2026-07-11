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
        _trayIcon.PairRequested += () => _hostService.ShowPairingWindow();
        _statusTimer.Tick += (_, _) => UpdateTrayStatus();
        _ = StartAsync();
    }

    private async Task StartAsync()
    {
        try
        {
            await _hostService.StartAsync(_cts.Token);
            // #region agent log
            DebugAgentLog.Write("H2", "TrayApplicationContext.cs:StartAsync", "Host startup succeeded", new
            {
                runId = "post-fix",
                status = _hostService.GetStatusText()
            });
            // #endregion
            UpdateTrayStatus();
            _trayIcon.SetReceivedDirectory(_hostService.ReceivedDirectory);
            _statusTimer.Start();
        }
        catch (Exception ex)
        {
            // #region agent log
            var inner = ex;
            var chain = new List<string>();
            while (inner is not null)
            {
                chain.Add($"{inner.GetType().Name}: {inner.Message}");
                inner = inner.InnerException;
            }
            DebugAgentLog.Write("H2", "TrayApplicationContext.cs:StartAsync", "Host startup failed", new
            {
                chain,
                rootType = ex.GetType().FullName
            });
            // #endregion
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
