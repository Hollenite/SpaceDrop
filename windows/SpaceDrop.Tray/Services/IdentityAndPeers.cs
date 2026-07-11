using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;

namespace SpaceDrop.Tray.Services;

public sealed class IdentityKeyStore
{
    private readonly string _dir;
    private readonly string _pfxPath;
    private readonly string _deviceIdPath;

    public IdentityKeyStore()
    {
        _dir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "SpaceDrop");
        Directory.CreateDirectory(_dir);
        _pfxPath = Path.Combine(_dir, "identity.pfx");
        _deviceIdPath = Path.Combine(_dir, "device_id.txt");
    }

    public string DeviceId { get; private set; } = "";
    public X509Certificate2 Certificate { get; private set; } = null!;
    public string FingerprintHex { get; private set; } = "";

    public void LoadOrCreate()
    {
        DeviceId = LoadOrCreateDeviceId();
        if (File.Exists(_pfxPath))
        {
            Certificate = new X509Certificate2(_pfxPath, (string?)null,
                X509KeyStorageFlags.UserKeySet | X509KeyStorageFlags.Exportable);
        }
        else
        {
            Certificate = CreateSelfSigned(DeviceId);
            File.WriteAllBytes(_pfxPath, Certificate.Export(X509ContentType.Pfx));
        }

        FingerprintHex = FingerprintOf(Certificate);
        Console.WriteLine($"Identity loaded id={DeviceId} fp={FingerprintHex[..16]}…");
    }

    public string CreatePairingQrUrl(int port = SpaceDrop.Tray.Protocol.SpaceDropProtocol.Port)
    {
        var nonce = Convert.ToHexString(RandomNumberGenerator.GetBytes(16)).ToLowerInvariant();
        var exp = DateTimeOffset.UtcNow.ToUnixTimeSeconds() + 300;
        var canonical = $"v=1&id={DeviceId}&fp={FingerprintHex}&port={port}&n={nonce}&exp={exp}";
        var sig = Sign(canonical);
        return $"spacedrop://pair?{canonical}&sig={sig}";
    }

    public bool VerifyOfferSignature(string canonical, string sigBase64Url)
    {
        try
        {
            var ecdsa = Certificate.GetECDsaPublicKey()
                ?? throw new InvalidOperationException("No ECDSA key");
            var data = Encoding.UTF8.GetBytes(canonical);
            var sig = Base64UrlDecode(sigBase64Url);
            return ecdsa.VerifyData(data, sig, HashAlgorithmName.SHA256);
        }
        catch
        {
            return false;
        }
    }

    private string Sign(string canonical)
    {
        var ecdsa = Certificate.GetECDsaPrivateKey()
            ?? throw new InvalidOperationException("No ECDSA private key");
        var sig = ecdsa.SignData(Encoding.UTF8.GetBytes(canonical), HashAlgorithmName.SHA256);
        return Base64UrlEncode(sig);
    }

    private string LoadOrCreateDeviceId()
    {
        if (File.Exists(_deviceIdPath))
        {
            var existing = File.ReadAllText(_deviceIdPath).Trim();
            if (!string.IsNullOrWhiteSpace(existing)) return existing;
        }

        // Prefer legacy DeviceIdStore file location used by older builds.
        var legacy = new DeviceIdStore().GetOrCreateDeviceId();
        File.WriteAllText(_deviceIdPath, legacy);
        return legacy;
    }

    private static X509Certificate2 CreateSelfSigned(string deviceId)
    {
        using var ecdsa = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var req = new CertificateRequest(
            $"CN=SpaceDrop {deviceId}",
            ecdsa,
            HashAlgorithmName.SHA256);
        req.CertificateExtensions.Add(
            new X509BasicConstraintsExtension(false, false, 0, false));
        req.CertificateExtensions.Add(
            new X509KeyUsageExtension(
                X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment,
                true));
        req.CertificateExtensions.Add(
            new X509EnhancedKeyUsageExtension(
                new OidCollection
                {
                    new Oid("1.3.6.1.5.5.7.3.1"), // serverAuth
                    new Oid("1.3.6.1.5.5.7.3.2")  // clientAuth
                },
                false));

        var cert = req.CreateSelfSigned(
            DateTimeOffset.UtcNow.AddDays(-1),
            DateTimeOffset.UtcNow.AddYears(10));
        return new X509Certificate2(
            cert.Export(X509ContentType.Pfx),
            (string?)null,
            X509KeyStorageFlags.UserKeySet | X509KeyStorageFlags.Exportable);
    }

    public static string FingerprintOf(X509Certificate2 cert)
    {
        var hash = SHA256.HashData(cert.RawData);
        return Convert.ToHexString(hash).ToLowerInvariant();
    }

    private static string Base64UrlEncode(byte[] data) =>
        Convert.ToBase64String(data).TrimEnd('=').Replace('+', '-').Replace('/', '_');

    private static byte[] Base64UrlDecode(string s)
    {
        var padded = s.Replace('-', '+').Replace('_', '/');
        switch (padded.Length % 4)
        {
            case 2: padded += "=="; break;
            case 3: padded += "="; break;
        }
        return Convert.FromBase64String(padded);
    }
}

public sealed class PairedPeer
{
    public string DeviceId { get; set; } = "";
    public string Fingerprint { get; set; } = "";
    public string DisplayName { get; set; } = "";
    public long PairedAtUnix { get; set; }
    public bool AlwaysAllow { get; set; } = true;
}

public sealed class PairedPeerStore
{
    private readonly string _path;
    private readonly object _gate = new();
    private List<PairedPeer> _peers = new();

    public PairedPeerStore()
    {
        var dir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "SpaceDrop");
        Directory.CreateDirectory(dir);
        _path = Path.Combine(dir, "paired_peers.json");
        Load();
    }

    public IReadOnlyList<PairedPeer> List()
    {
        lock (_gate) return _peers.ToList();
    }

    public bool IsPairedFingerprint(string fingerprint)
    {
        lock (_gate)
            return _peers.Any(p => p.Fingerprint.Equals(fingerprint, StringComparison.OrdinalIgnoreCase));
    }

    public bool IsPairedDeviceId(string deviceId)
    {
        lock (_gate)
            return _peers.Any(p => p.DeviceId.Equals(deviceId, StringComparison.OrdinalIgnoreCase));
    }

    public PairedPeer? FindByFingerprint(string fingerprint)
    {
        lock (_gate)
            return _peers.FirstOrDefault(p =>
                p.Fingerprint.Equals(fingerprint, StringComparison.OrdinalIgnoreCase));
    }

    public void Upsert(PairedPeer peer)
    {
        lock (_gate)
        {
            _peers.RemoveAll(p =>
                p.DeviceId.Equals(peer.DeviceId, StringComparison.OrdinalIgnoreCase) ||
                p.Fingerprint.Equals(peer.Fingerprint, StringComparison.OrdinalIgnoreCase));
            _peers.Add(peer);
            Save();
        }
    }

    public bool Forget(string deviceId)
    {
        lock (_gate)
        {
            var removed = _peers.RemoveAll(p =>
                p.DeviceId.Equals(deviceId, StringComparison.OrdinalIgnoreCase)) > 0;
            if (removed) Save();
            return removed;
        }
    }

    private void Load()
    {
        if (!File.Exists(_path)) return;
        try
        {
            var json = File.ReadAllText(_path);
            _peers = JsonSerializer.Deserialize<List<PairedPeer>>(json) ?? new();
        }
        catch
        {
            _peers = new();
        }
    }

    private void Save()
    {
        var json = JsonSerializer.Serialize(_peers, new JsonSerializerOptions { WriteIndented = true });
        File.WriteAllText(_path, json);
    }
}
