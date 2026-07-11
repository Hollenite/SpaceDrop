using System.Text.Json;

namespace SpaceDrop.AcousticFsk.Tests;

public class FixtureGenerator
{
    public static void GenerateFixtures(string fixturesRoot)
    {
        var wavDir = Path.Combine(fixturesRoot, "wav");
        Directory.CreateDirectory(wavDir);

        var entries = new List<FixtureEntry>
        {
            MakeEntry("clean-primary.wav", "550e8400-e29b-41d4-a716-446655440000", 100, shouldDecode: true),
            MakeEntry("clean-secondary.wav", "6ba7b810-9dad-11d1-80b4-00c04fd430c8", 0, shouldDecode: true),
            MakeEntry("clean-rotated-epoch.wav", "550e8400-e29b-41d4-a716-446655440000", 101, shouldDecode: true),
            MakeEntry("bad-crc.wav", "550e8400-e29b-41d4-a716-446655440000", 100, shouldDecode: false, corruptCrc: true)
        };

        var encoder = new FskEncoder();
        foreach (var entry in entries)
        {
            var bits = AcousticToken.BuildFrameBits(entry.DeviceId, entry.Epoch);
            if (entry.FileName == "bad-crc.wav")
            {
                bits[^1] ^= 1;
            }
            var pcm = encoder.EncodeBits(bits);
            File.WriteAllBytes(Path.Combine(wavDir, entry.FileName), WavIo.WriteMonoPcm16(pcm));
        }

        var manifest = entries.Select(e => new
        {
            file = $"wav/{e.FileName}",
            deviceId = e.DeviceId,
            epoch = e.Epoch,
            tokenHex = AcousticToken.TokenHex(AcousticToken.ComputeToken20(e.DeviceId, e.Epoch)),
            shouldDecode = e.ShouldDecode
        }).ToArray();

        var json = JsonSerializer.Serialize(manifest, new JsonSerializerOptions { WriteIndented = true });
        File.WriteAllText(Path.Combine(fixturesRoot, "expected-tokens.json"), json);
    }

    private static FixtureEntry MakeEntry(string fileName, string deviceId, int epoch, bool shouldDecode, bool corruptCrc = false) =>
        new(fileName, deviceId, epoch, shouldDecode, corruptCrc);

    private sealed record FixtureEntry(string FileName, string DeviceId, int Epoch, bool ShouldDecode, bool CorruptCrc);
}
