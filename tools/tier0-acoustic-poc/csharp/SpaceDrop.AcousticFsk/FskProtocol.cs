namespace SpaceDrop.AcousticFsk;

public static class FskProtocol
{
    public const int SampleRate = 44_100;
    public const double Amplitude = 0.4;
    public const double PreambleHz = 19_000;
    public const double Bit0Hz = 18_500;
    public const double Bit1Hz = 19_500;
    public const int PreambleMs = 300;
    public const int SymbolMs = 100;
    public const int InterFrameGapMs = 500;
    public const int PayloadBits = 24;
    public const int TokenBits = 20;
    public const int CrcBits = 4;
    public const int PreambleLockMs = 200;
    public const int CooldownMs = 500;
    public const double SnrThresholdDb = 12.0;
    public const double RatioThreshold = 1.2;
    public const int EpochSeconds = 30;

    public static int FrameActiveMs => PreambleMs + PayloadBits * SymbolMs;

    public static int SamplesForMs(int ms, int sampleRate = SampleRate) =>
        sampleRate * ms / 1000;
}
