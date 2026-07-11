using NAudio.Wave;
using SpaceDrop.AcousticFsk;

namespace SpaceDrop.AcousticEmitter;

internal sealed class FskLoopWaveProvider : IWaveProvider
{
    private readonly short[] _samples;
    private int _position;

    public FskLoopWaveProvider(short[] samples, int sampleRate = FskProtocol.SampleRate)
    {
        _samples = samples;
        WaveFormat = new WaveFormat(sampleRate, 16, 1);
    }

    public WaveFormat WaveFormat { get; }

    public int Read(byte[] buffer, int offset, int count)
    {
        if (_samples.Length == 0) return 0;

        var bytesWritten = 0;
        while (bytesWritten < count)
        {
            if (_position >= _samples.Length)
            {
                _position = 0;
            }

            var sample = _samples[_position++];
            buffer[offset + bytesWritten++] = (byte)(sample & 0xFF);
            if (bytesWritten >= count) break;
            buffer[offset + bytesWritten++] = (byte)((sample >> 8) & 0xFF);
        }

        return bytesWritten;
    }
}
