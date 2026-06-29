namespace SpaceDrop.Tray.Services;

public sealed class DeviceIdStore
{
    private readonly string _path;

    public DeviceIdStore()
    {
        var dir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "SpaceDrop");
        Directory.CreateDirectory(dir);
        _path = Path.Combine(dir, "device_id.txt");
    }

    public string GetOrCreateDeviceId()
    {
        if (File.Exists(_path))
        {
            var existing = File.ReadAllText(_path).Trim();
            if (!string.IsNullOrEmpty(existing))
            {
                return existing;
            }
        }

        var deviceId = Guid.NewGuid().ToString();
        File.WriteAllText(_path, deviceId);
        return deviceId;
    }
}
