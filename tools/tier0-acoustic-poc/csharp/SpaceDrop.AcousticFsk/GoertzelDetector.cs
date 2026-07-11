namespace SpaceDrop.AcousticFsk;

public sealed class GoertzelDetector
{
    public const int DefaultBlockSize = 2048;

    private readonly int _blockSize;
    private readonly double _omega;
    private readonly double _coeff;

    public GoertzelDetector(int sampleRate, double targetFrequencyHz, int blockSize = DefaultBlockSize)
    {
        _blockSize = blockSize;
        var k = (int)Math.Round(0.5 + blockSize * targetFrequencyHz / sampleRate);
        _omega = 2.0 * Math.PI * k / blockSize;
        _coeff = 2.0 * Math.Cos(_omega);
    }

    public double Magnitude(ReadOnlySpan<short> samples)
    {
        var n = samples.Length;
        var sPrev = 0.0;
        var sPrev2 = 0.0;
        for (var i = 0; i < n; i++)
        {
            var x = samples[i] / 32768.0;
            var s = x + _coeff * sPrev - sPrev2;
            sPrev2 = sPrev;
            sPrev = s;
        }
        var real = sPrev - sPrev2 * Math.Cos(_omega);
        var imag = sPrev2 * Math.Sin(_omega);
        return Math.Sqrt(real * real + imag * imag) / n;
    }
}
