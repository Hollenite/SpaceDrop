using System.Windows.Forms;

namespace SpaceDrop.Tray;

internal static class Program
{
    [STAThread]
    private static void Main()
    {
        using var singleInstanceMutex = new Mutex(true, "SpaceDrop.Tray.SingleInstance", out var createdNew);
        if (!createdNew)
        {
            return;
        }

        ApplicationConfiguration.Initialize();
        Application.Run(new TrayApplicationContext());
    }
}
