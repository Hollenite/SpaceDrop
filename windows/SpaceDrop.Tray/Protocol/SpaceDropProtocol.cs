namespace SpaceDrop.Tray.Protocol;

public static class SpaceDropProtocol
{
    public const int Port = 58392;
    public const string ServiceType = "_spacedrop._tcp.local.";
    public const int ProtocolVersion = 2;
    public const int DefaultAcousticArmMs = 20_000;
    public const int MaxAcousticArmMs = 60_000;
    public const long MaxFileBytes = 2L * 1024 * 1024 * 1024; // 2 GiB
    public const long MaxBatchBytes = 4L * 1024 * 1024 * 1024; // 4 GiB

    public static readonly Guid ServiceUuid = Guid.Parse("0000a501-0000-1000-8000-00805f9b34fb");
    public static readonly Guid DeviceIdCharacteristicUuid = Guid.Parse("0000a502-0000-1000-8000-00805f9b34fb");
    public static readonly Guid DeviceNameCharacteristicUuid = Guid.Parse("0000a503-0000-1000-8000-00805f9b34fb");
    public static readonly Guid EndpointCharacteristicUuid = Guid.Parse("0000a504-0000-1000-8000-00805f9b34fb");
    public static readonly Guid ControlCharacteristicUuid = Guid.Parse("0000a505-0000-1000-8000-00805f9b34fb");
}
