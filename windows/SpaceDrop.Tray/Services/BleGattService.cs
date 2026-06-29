using System.Runtime.InteropServices.WindowsRuntime;
using System.Text;
using System.Text.Json;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Devices.Bluetooth.GenericAttributeProfile;
using Windows.Storage.Streams;
using SpaceDrop.Tray.Protocol;

namespace SpaceDrop.Tray.Services;

public sealed class BleGattService : IAsyncDisposable
{
    private GattServiceProvider? _serviceProvider;
    private BluetoothLEAdvertisementPublisher? _publisher;
    private string _endpointJson = "{}";

    public bool IsAvailable { get; private set; }
    public string? UnavailableReason { get; private set; }

    public async Task StartAsync(string deviceId, string deviceName, string host, int port)
    {
        await StopAsync();

        var adapter = await BluetoothAdapter.GetDefaultAsync();
        if (adapter is null)
        {
            IsAvailable = false;
            UnavailableReason = "No Bluetooth adapter found";
            Console.WriteLine(UnavailableReason);
            return;
        }

        if (!adapter.IsPeripheralRoleSupported)
        {
            IsAvailable = false;
            UnavailableReason = "Bluetooth peripheral role not supported on this PC";
            Console.WriteLine(UnavailableReason);
            return;
        }

        _endpointJson = JsonSerializer.Serialize(new { host, port });

        var serviceResult = await GattServiceProvider.CreateAsync(SpaceDropProtocol.ServiceUuid);
        if (serviceResult.Error != BluetoothError.Success)
        {
            IsAvailable = false;
            UnavailableReason = $"GATT service creation failed: {serviceResult.Error}";
            Console.WriteLine(UnavailableReason);
            return;
        }

        _serviceProvider = serviceResult.ServiceProvider;

        AddReadCharacteristic(
            SpaceDropProtocol.DeviceIdCharacteristicUuid,
            () => Encoding.UTF8.GetBytes(deviceId));
        AddReadCharacteristic(
            SpaceDropProtocol.DeviceNameCharacteristicUuid,
            () => Encoding.UTF8.GetBytes(deviceName));
        AddReadCharacteristic(
            SpaceDropProtocol.EndpointCharacteristicUuid,
            () => Encoding.UTF8.GetBytes(_endpointJson));

        _serviceProvider.StartAdvertising(new GattServiceProviderAdvertisingParameters
        {
            IsConnectable = true,
            IsDiscoverable = true
        });

        _publisher = new BluetoothLEAdvertisementPublisher
        {
            Advertisement =
            {
                ServiceUuids = { SpaceDropProtocol.ServiceUuid }
            }
        };
        _publisher.Start();

        IsAvailable = true;
        UnavailableReason = null;
        Console.WriteLine($"BLE GATT advertising started for {deviceName} ({deviceId})");
    }

    public Task UpdateEndpointAsync(string host, int port)
    {
        _endpointJson = JsonSerializer.Serialize(new { host, port });
        return Task.CompletedTask;
    }

    private void AddReadCharacteristic(Guid uuid, Func<byte[]> valueFactory)
    {
        if (_serviceProvider is null) return;

        var parameters = new GattLocalCharacteristicParameters
        {
            CharacteristicProperties = GattCharacteristicProperties.Read,
            UserDescription = uuid.ToString()
        };

        var result = _serviceProvider.Service.CreateCharacteristicAsync(uuid, parameters)
            .AsTask()
            .GetAwaiter()
            .GetResult();
        if (result.Error != BluetoothError.Success)
        {
            throw new InvalidOperationException($"Failed to create characteristic {uuid}: {result.Error}");
        }

        result.Characteristic.ReadRequested += async (_, args) =>
        {
            var deferral = args.GetDeferral();
            try
            {
                var request = await args.GetRequestAsync();
                request?.RespondWithValue(BufferFromBytes(valueFactory()));
            }
            finally
            {
                deferral.Complete();
            }
        };
    }

    private static IBuffer BufferFromBytes(byte[] bytes) =>
        bytes.AsBuffer();

    public Task StopAsync()
    {
        _publisher?.Stop();
        _publisher = null;

        if (_serviceProvider is not null)
        {
            _serviceProvider.StopAdvertising();
            _serviceProvider = null;
        }

        IsAvailable = false;
        return Task.CompletedTask;
    }

    public async ValueTask DisposeAsync()
    {
        await StopAsync();
    }
}
