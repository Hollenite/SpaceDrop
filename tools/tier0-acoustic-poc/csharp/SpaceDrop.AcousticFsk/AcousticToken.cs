using System.Security.Cryptography;
using System.Text;

namespace SpaceDrop.AcousticFsk;

public static class AcousticToken
{
    public static int EpochFromUnixSeconds(long unixSeconds) =>
        (int)(unixSeconds / FskProtocol.EpochSeconds);

    public static int ComputeToken20(string deviceId, int epoch)
    {
        var idBytes = Encoding.UTF8.GetBytes(deviceId);
        var epochBytes = new byte[]
        {
            (byte)(epoch >> 24),
            (byte)(epoch >> 16),
            (byte)(epoch >> 8),
            (byte)epoch
        };
        var input = new byte[idBytes.Length + epochBytes.Length];
        Buffer.BlockCopy(idBytes, 0, input, 0, idBytes.Length);
        Buffer.BlockCopy(epochBytes, 0, input, idBytes.Length, epochBytes.Length);
        var hash = SHA256.HashData(input);
        return ((hash[0] & 0xFF) << 12) | ((hash[1] & 0xFF) << 4) | ((hash[2] & 0xFF) >> 4);
    }

    public static int[] BuildFrameBits(string deviceId, int epoch)
    {
        var token20 = ComputeToken20(deviceId, epoch);
        var crc = Crc4.Compute(token20);
        var bits = new int[FskProtocol.PayloadBits];
        for (var i = 0; i < FskProtocol.TokenBits; i++)
        {
            bits[i] = (token20 >> (FskProtocol.TokenBits - 1 - i)) & 1;
        }
        for (var i = 0; i < FskProtocol.CrcBits; i++)
        {
            bits[FskProtocol.TokenBits + i] = (crc >> (FskProtocol.CrcBits - 1 - i)) & 1;
        }
        return bits;
    }

    public static string TokenHex(int token20) =>
        $"0x{(token20 & 0xFFFFF):X5}";
}
