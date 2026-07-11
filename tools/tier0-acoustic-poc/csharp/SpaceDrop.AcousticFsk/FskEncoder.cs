namespace SpaceDrop.AcousticFsk;

public sealed class FskEncoder
{
    private readonly int _sampleRate;
    private readonly double _amplitude;

    public FskEncoder(int sampleRate = FskProtocol.SampleRate, double amplitude = FskProtocol.Amplitude)
    {
        _sampleRate = sampleRate;
        _amplitude = amplitude;
    }

    public short[] EncodeFrame(string deviceId, int epoch) =>
        EncodeBits(AcousticToken.BuildFrameBits(deviceId, epoch));

    public short[] EncodeFrameWithGap(string deviceId, int epoch, int gapMs = FskProtocol.InterFrameGapMs)
    {
        var frame = EncodeFrame(deviceId, epoch);
        var gap = Silence(gapMs);
        var combined = new short[frame.Length + gap.Length];
        frame.CopyTo(combined, 0);
        gap.CopyTo(combined, frame.Length);
        return combined;
    }

    public short[] EncodeBits(int[] bits)
    {
        if (bits.Length != FskProtocol.PayloadBits)
        {
            throw new ArgumentException($"Expected {FskProtocol.PayloadBits} bits");
        }

        var segments = new List<short[]>
        {
            SineSegment(FskProtocol.PreambleHz, FskProtocol.PreambleMs)
        };
        foreach (var bit in bits)
        {
            segments.Add(SineSegment(bit == 1 ? FskProtocol.Bit1Hz : FskProtocol.Bit0Hz, FskProtocol.SymbolMs));
        }

        var total = segments.Sum(s => s.Length);
        var output = new short[total];
        var offset = 0;
        foreach (var segment in segments)
        {
            segment.CopyTo(output, offset);
            offset += segment.Length;
        }
        return output;
    }

    public short[] Silence(int ms) => new short[FskProtocol.SamplesForMs(ms, _sampleRate)];

    private short[] SineSegment(double frequencyHz, int durationMs)
    {
        var count = FskProtocol.SamplesForMs(durationMs, _sampleRate);
        var output = new short[count];
        var phaseInc = 2.0 * Math.PI * frequencyHz / _sampleRate;
        var phase = 0.0;
        for (var i = 0; i < count; i++)
        {
            output[i] = (short)Math.Clamp(Math.Sin(phase) * _amplitude * short.MaxValue, short.MinValue, short.MaxValue);
            phase += phaseInc;
            if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI;
        }
        return output;
    }
}
