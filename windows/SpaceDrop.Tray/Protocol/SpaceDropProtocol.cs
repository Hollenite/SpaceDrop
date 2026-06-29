namespace SpaceDrop.Tray.Protocol;

public static class SpaceDropProtocol
{
    public const int Port = 58392;
    public const string SharedToken = "spacedrop-dev-token";
    public const string ServiceType = "_spacedrop._tcp.local.";

    // Valid hex-only UUIDs (a501-a504 = SpaceDrop service namespace)
    public static readonly Guid ServiceUuid = Guid.Parse("0000a501-0000-1000-8000-00805f9b34fb");
    public static readonly Guid DeviceIdCharacteristicUuid = Guid.Parse("0000a502-0000-1000-8000-00805f9b34fb");
    public static readonly Guid DeviceNameCharacteristicUuid = Guid.Parse("0000a503-0000-1000-8000-00805f9b34fb");
    public static readonly Guid EndpointCharacteristicUuid = Guid.Parse("0000a504-0000-1000-8000-00805f9b34fb");
}
