namespace SpaceDrop.AcousticFsk;

public static class Crc4
{
    private const int Poly = 0x3;

    public static int Compute(int token20)
    {
        var crc = 0;
        for (var i = FskProtocol.TokenBits - 1; i >= 0; i--)
        {
            var bit = (token20 >> i) & 1;
            crc ^= bit;
            crc = (crc & 8) != 0 ? ((crc << 1) ^ Poly) & 0xF : (crc << 1) & 0xF;
        }
        return crc;
    }

    public static bool Verify(int token20, int crc4) => Compute(token20) == (crc4 & 0xF);
}
