# Regenerate golden WAV fixtures from C# encoder
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $root "csharp\SpaceDrop.AcousticFsk.Tests")
dotnet test --filter "FullyQualifiedName~FixtureGeneratorTests" --no-build 2>$null
if ($LASTEXITCODE -ne 0) {
    dotnet test --filter "FullyQualifiedName~FixtureGeneratorTests"
}
Pop-Location
Write-Host "Fixtures written to $root\fixtures\"
