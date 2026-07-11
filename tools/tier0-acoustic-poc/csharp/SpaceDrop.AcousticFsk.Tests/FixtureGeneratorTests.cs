using Xunit;

namespace SpaceDrop.AcousticFsk.Tests;

public class FixtureGeneratorTests
{
    [Fact]
    public void GenerateGoldenFixtures()
    {
        var fixturesRoot = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "..", "..", "..", "..", "..", "fixtures"));
        FixtureGenerator.GenerateFixtures(fixturesRoot);
        Assert.True(File.Exists(Path.Combine(fixturesRoot, "expected-tokens.json")));
        Assert.True(File.Exists(Path.Combine(fixturesRoot, "wav", "clean-primary.wav")));
    }
}
