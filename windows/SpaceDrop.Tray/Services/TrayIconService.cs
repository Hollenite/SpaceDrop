using System.Diagnostics;
using System.Drawing;

namespace SpaceDrop.Tray.Services;

public enum TrayStatus
{
    Running,
    BleUnavailable,
    Error
}

public sealed class TrayIconService : IDisposable
{
    private readonly NotifyIcon _notifyIcon;
    private readonly ToolStripMenuItem _statusItem;
    private readonly ToolStripMenuItem _receivedFolderItem;
    private readonly ToolStripMenuItem _exitItem;
    private string _receivedDirectory = string.Empty;

    public TrayIconService()
    {
        _statusItem = new ToolStripMenuItem("Status: starting…") { Enabled = false };
        _receivedFolderItem = new ToolStripMenuItem(
            "Open received folder",
            null,
            (_, _) => OpenReceivedFolder());
        _exitItem = new ToolStripMenuItem("Exit", null, (_, _) => ExitRequested?.Invoke());

        _notifyIcon = new NotifyIcon
        {
            Icon = SystemIcons.Application,
            Visible = true,
            Text = "SpaceDrop",
            ContextMenuStrip = new ContextMenuStrip()
        };
        _notifyIcon.ContextMenuStrip!.Items.Add(_statusItem);
        _notifyIcon.ContextMenuStrip.Items.Add(_receivedFolderItem);
        _notifyIcon.ContextMenuStrip.Items.Add(new ToolStripSeparator());
        _notifyIcon.ContextMenuStrip.Items.Add(_exitItem);
    }

    public event Action? ExitRequested;

    public void SetReceivedDirectory(string path)
    {
        _receivedDirectory = path;
        _receivedFolderItem.Text = $"Open received folder ({ShortPath(path)})";
    }

    public void SetStatus(TrayStatus status, string detail)
    {
        _statusItem.Text = detail;
        _notifyIcon.Text = detail.Length > 63 ? detail[..63] : detail;
        _notifyIcon.Icon = status switch
        {
            TrayStatus.Error => SystemIcons.Error,
            TrayStatus.BleUnavailable => SystemIcons.Warning,
            _ => SystemIcons.Application
        };
    }

    private void OpenReceivedFolder()
    {
        if (string.IsNullOrWhiteSpace(_receivedDirectory))
        {
            return;
        }

        Directory.CreateDirectory(_receivedDirectory);
        Process.Start(new ProcessStartInfo
        {
            FileName = _receivedDirectory,
            UseShellExecute = true
        });
    }

    private static string ShortPath(string path)
    {
        if (path.Length <= 48) return path;
        return "…" + path[^45..];
    }

    public void Dispose()
    {
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
    }
}
