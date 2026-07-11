using System.Drawing;
using System.Windows.Forms;
using QRCoder;

namespace SpaceDrop.Tray.Services;

/// <summary>Shows a pairing QR the phone can scan. Pairing window is "open" while this form is visible.</summary>
public sealed class PairingForm : Form
{
    private readonly IdentityKeyStore _identity;
    private readonly PairedPeerStore _peers;
    private readonly PictureBox _qrBox;
    private readonly Label _hint;
    private readonly ListBox _peerList;

    public bool IsOpen { get; private set; }

    public PairingForm(IdentityKeyStore identity, PairedPeerStore peers)
    {
        _identity = identity;
        _peers = peers;

        Text = "SpaceDrop — Pair phone";
        Width = 420;
        Height = 560;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        StartPosition = FormStartPosition.CenterScreen;

        _hint = new Label
        {
            Dock = DockStyle.Top,
            Height = 48,
            TextAlign = ContentAlignment.MiddleCenter,
            Text = "Scan this QR with the SpaceDrop app"
        };

        _qrBox = new PictureBox
        {
            Dock = DockStyle.Top,
            Height = 320,
            SizeMode = PictureBoxSizeMode.Zoom
        };

        var refresh = new Button
        {
            Text = "Refresh QR",
            Dock = DockStyle.Top,
            Height = 32
        };
        refresh.Click += (_, _) => RenderQr();

        var peersLabel = new Label
        {
            Dock = DockStyle.Top,
            Height = 24,
            Text = "Paired phones:",
            TextAlign = ContentAlignment.MiddleLeft,
            Padding = new Padding(8, 0, 0, 0)
        };

        _peerList = new ListBox { Dock = DockStyle.Fill };

        var forget = new Button
        {
            Text = "Forget selected",
            Dock = DockStyle.Bottom,
            Height = 32
        };
        forget.Click += (_, _) => ForgetSelected();

        Controls.Add(_peerList);
        Controls.Add(forget);
        Controls.Add(peersLabel);
        Controls.Add(refresh);
        Controls.Add(_qrBox);
        Controls.Add(_hint);

        Load += (_, _) =>
        {
            IsOpen = true;
            RenderQr();
            RefreshPeers();
        };
        FormClosed += (_, _) => IsOpen = false;
    }

    private void RenderQr()
    {
        var url = _identity.CreatePairingQrUrl();
        using var generator = new QRCodeGenerator();
        using var data = generator.CreateQrCode(url, QRCodeGenerator.ECCLevel.Q);
        using var qr = new QRCode(data);
        var bmp = qr.GetGraphic(6);
        _qrBox.Image?.Dispose();
        _qrBox.Image = bmp;
        Console.WriteLine($"Pairing QR ready fp={_identity.FingerprintHex[..16]}…");
    }

    private void RefreshPeers()
    {
        _peerList.Items.Clear();
        foreach (var p in _peers.List())
        {
            _peerList.Items.Add($"{p.DisplayName} ({p.DeviceId[..8]}…)");
        }
    }

    private void ForgetSelected()
    {
        var idx = _peerList.SelectedIndex;
        if (idx < 0) return;
        var list = _peers.List();
        if (idx >= list.Count) return;
        _peers.Forget(list[idx].DeviceId);
        RefreshPeers();
    }

    public void NotifyPeersChanged() =>
        BeginInvoke(RefreshPeers);
}
