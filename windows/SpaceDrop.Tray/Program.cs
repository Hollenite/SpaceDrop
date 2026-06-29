using System.Windows.Forms;
using SpaceDrop.Tray.Services;

namespace SpaceDrop.Tray;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        // #region agent log
        DebugAgentLog.Write("H1", "Program.cs:Main", "Testing Guid.Parse on protocol UUID candidate", new
        {
            candidate = "0000a501-0000-1000-8000-00805f9b34fb",
            runId = "post-fix"
        });
        try
        {
            _ = Guid.Parse("0000a501-0000-1000-8000-00805f9b34fb");
            DebugAgentLog.Write("H1", "Program.cs:Main", "Guid.Parse succeeded (post-fix)", new { runId = "post-fix" });
        }
        catch (Exception ex)
        {
            DebugAgentLog.Write("H1", "Program.cs:Main", "Guid.Parse failed", new
            {
                type = ex.GetType().FullName,
                ex.Message,
                inner = ex.InnerException?.Message
            });
        }
        // #endregion

        using var singleInstanceMutex = new Mutex(true, "SpaceDrop.Tray.SingleInstance", out var createdNew);
        if (!createdNew)
        {
            return;
        }

        ApplicationConfiguration.Initialize();
        Application.Run(new TrayApplicationContext());
    }
}
