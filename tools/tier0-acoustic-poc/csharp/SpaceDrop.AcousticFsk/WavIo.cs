namespace SpaceDrop.AcousticFsk;

public static class WavIo
{
    public static byte[] WriteMonoPcm16(short[] samples, int sampleRate = FskProtocol.SampleRate)
    {
        var dataSize = samples.Length * 2;
        using var stream = new MemoryStream(44 + dataSize);
        using var writer = new BinaryWriter(stream);
        writer.Write("RIFF"u8);
        writer.Write(36 + dataSize);
        writer.Write("WAVE"u8);
        writer.Write("fmt "u8);
        writer.Write(16);
        writer.Write((short)1);
        writer.Write((short)1);
        writer.Write(sampleRate);
        writer.Write(sampleRate * 2);
        writer.Write((short)2);
        writer.Write((short)16);
        writer.Write("data"u8);
        writer.Write(dataSize);
        foreach (var sample in samples)
        {
            writer.Write(sample);
        }
        return stream.ToArray();
    }
}
