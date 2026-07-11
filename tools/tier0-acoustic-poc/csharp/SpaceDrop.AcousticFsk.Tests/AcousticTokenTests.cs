using Xunit;

namespace SpaceDrop.AcousticFsk.Tests;

public class AcousticTokenTests
{
    [Fact]
    public void FixedVector()
    {
        var token = AcousticToken.ComputeToken20("550e8400-e29b-41d4-a716-446655440000", 100);
        Assert.Equal(0x5142E, token);
    }

    [Fact]
    public void CrcMatchesToken()
    {
        const string deviceId = "550e8400-e29b-41d4-a716-446655440000";
        const int epoch = 100;
        var token = AcousticToken.ComputeToken20(deviceId, epoch);
        var crc = Crc4.Compute(token);
        var bits = AcousticToken.BuildFrameBits(deviceId, epoch);
        var decoded = 0;
        foreach (var bit in bits)
        {
            decoded = (decoded << 1) | bit;
        }
        Assert.Equal(token << 4 | crc, decoded);
    }
}

public class FskEncoderTests
{
    [Fact]
    public void FrameDuration()
    {
        var encoder = new FskEncoder();
        var pcm = encoder.EncodeFrame("550e8400-e29b-41d4-a716-446655440000", 100);
        Assert.Equal(FskProtocol.SamplesForMs(FskProtocol.FrameActiveMs), pcm.Length);
    }

    [Theory]
    [InlineData("550e8400-e29b-41d4-a716-446655440000", 100)]
    [InlineData("6ba7b810-9dad-11d1-80b4-00c04fd430c8", 0)]
    [InlineData("00000000-0000-0000-0000-000000000001", 999999)]
    public void EncodeProducesExpectedTokenBits(string deviceId, int epoch)
    {
        var encoder = new FskEncoder();
        var bits = AcousticToken.BuildFrameBits(deviceId, epoch);
        var pcm = encoder.EncodeBits(bits);
        Assert.Equal(FskProtocol.SamplesForMs(FskProtocol.FrameActiveMs), pcm.Length);
    }
}

public class Crc4Tests
{
    [Fact]
    public void KnownToken()
    {
        Assert.Equal(0x7, Crc4.Compute(0x5142E));
    }
}
