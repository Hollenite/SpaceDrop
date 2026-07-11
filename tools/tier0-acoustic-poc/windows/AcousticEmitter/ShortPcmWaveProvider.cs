using NAudio.Wave;
using SpaceDrop.AcousticFsk;

namespace SpaceDrop.AcousticEmitter;

internal sealed class ShortPcmWaveProvider : IWaveProvider
{
    private readonly short[] _samples;
    private int _position;

    public ShortPcmWaveProvider(short[] samples, int sampleRate = FskProtocol.SampleRate)
    {
        _samples = samples;
        WaveFormat = new WaveFormat(sampleRate, 16, 1);
    }

    public WaveFormat WaveFormat { get; }

    public int Read(byte[] buffer, int offset, int count)
    {
        var bytesWritten = 0;
        while (bytesWritten < count && _position < _samples.Length)
        {
            var sample = _samples[_position++];
            buffer[offset + bytesWritten++] = (byte)(sample & 0xFF);
            if (bytesWritten >= count) break;
            buffer[offset + bytesWritten++] = (byte)((sample >> 8) & 0xFF);
        }
        return bytesWritten;
    }
}
