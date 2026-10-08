$ErrorActionPreference = 'Stop'

$projectRoot = $PSScriptRoot
$driveLetter = @('Z', 'Y', 'X') |
    Where-Object { -not (Test-Path -LiteralPath "${_}:\") } |
    Select-Object -First 1

if (-not $driveLetter) {
    throw 'Drive letters Z:, Y:, and X: are already in use.'
}

$mapped = $false
$locationPushed = $false
try {
    subst.exe "${driveLetter}:" $projectRoot
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the temporary build drive.' }
    $mapped = $true

    Push-Location "${driveLetter}:\"
    $locationPushed = $true
    & .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }
} finally {
    if ($locationPushed) { Pop-Location }
    if ($mapped) { subst.exe "${driveLetter}:" /D }
}
