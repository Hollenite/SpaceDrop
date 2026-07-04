using System.Text.Json;

namespace SpaceDrop.Tray;

internal static class DebugAgentLog
{
    private static readonly string LogPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
        "SpaceDrop",
        "debug-2023fe.log");

    // Also write beside repo when running from VS with known workspace path.
    private static readonly string WorkspaceLogPath =
        @"D:\PROJECTS\SpaceDrop\debug-2023fe.log";

    public static void Write(string hypothesisId, string location, string message, object? data = null)
    {
        // #region agent log
        try
        {
            var payload = JsonSerializer.Serialize(new
            {
                sessionId = "2023fe",
                hypothesisId,
                location,
                message,
                data,
                timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
            });
            Directory.CreateDirectory(Path.GetDirectoryName(LogPath)!);
            File.AppendAllText(LogPath, payload + Environment.NewLine);
            try
            {
                Directory.CreateDirectory(Path.GetDirectoryName(WorkspaceLogPath)!);
                File.AppendAllText(WorkspaceLogPath, payload + Environment.NewLine);
            }
            catch
            {
                // workspace path may differ on other machines
            }
        }
        catch
        {
            // ignore logging failures
        }
        // #endregion
    }
}
