namespace SpaceDrop.Tray.Services;

public static class ReceivedPathResolver
{
    public static string Resolve()
    {
        var overridePath = Environment.GetEnvironmentVariable("SPACEDROP_RECEIVED_DIR");
        if (!string.IsNullOrWhiteSpace(overridePath))
        {
            return overridePath;
        }

        var dir = AppContext.BaseDirectory;
        for (var depth = 0; depth < 10; depth++)
        {
            if (File.Exists(Path.Combine(dir, "spacedrop_pc.py")))
            {
                return Path.Combine(dir, "received");
            }

            var parent = Directory.GetParent(dir);
            if (parent is null)
            {
                break;
            }

            dir = parent.FullName;
        }

        return Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
            "SpaceDrop",
            "received");
    }
}
