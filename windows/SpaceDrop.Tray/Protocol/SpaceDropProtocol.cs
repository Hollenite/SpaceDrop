namespace SpaceDrop.Tray.Protocol;

public static class SpaceDropProtocol
{
    public const int Port = 58392;
    public const string SharedToken = "spacedrop-dev-token";
    public const string ServiceType = "_spacedrop._tcp.local.";

    public static readonly Guid ServiceUuid = Guid.Parse("0000sd01-0000-1000-8000-00805f9b34fb");
    public static readonly Guid DeviceIdCharacteristicUuid = Guid.Parse("0000sd02-0000-1000-8000-00805f9b34fb");
    public static readonly Guid DeviceNameCharacteristicUuid = Guid.Parse("0000sd03-0000-1000-8000-00805f9b34fb");
    public static readonly Guid EndpointCharacteristicUuid = Guid.Parse("0000sd04-0000-1000-8000-00805f9b34fb");
}
