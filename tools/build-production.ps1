# Uses the owner-approved local production identity; creates no keys.
param([switch]$Checks)
$ErrorActionPreference = 'Stop'
$signingDirectory = Join-Path $env:USERPROFILE '.codex\signing\trailnest-production'
$keystorePath = Join-Path $signingDirectory 'trailnest-production.p12'
$passwordPath = Join-Path $signingDirectory 'password.dpapi'
if (!(Test-Path -LiteralPath $keystorePath) -or !(Test-Path -LiteralPath $passwordPath)) {
    throw 'The dedicated production keystore or DPAPI password is unavailable. Do not substitute LAT/debug keys.'
}
$names = @('TRAILNEST_RELEASE_KEYSTORE','TRAILNEST_RELEASE_KEY_ALIAS','TRAILNEST_RELEASE_STORE_PASSWORD','TRAILNEST_RELEASE_KEY_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$securePassword = (Get-Content -LiteralPath $passwordPath -Raw).Trim() | ConvertTo-SecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $env:TRAILNEST_RELEASE_KEYSTORE = $keystorePath
    $env:TRAILNEST_RELEASE_KEY_ALIAS = 'trailnest-production'
    $env:TRAILNEST_RELEASE_STORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    $env:TRAILNEST_RELEASE_KEY_PASSWORD = $env:TRAILNEST_RELEASE_STORE_PASSWORD
    $tasks = @(':app:assembleRelease')
    if ($Checks) { $tasks += ':app:testDebugUnitTest', ':app:lintRelease' }
    & .\gradlew.bat @tasks --offline --max-workers=2 --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Production build/checks failed.' }
    $identity = Get-Content -LiteralPath '.\config\production-signing.json' -Raw | ConvertFrom-Json
    $apksigner = Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools\35.0.0\apksigner.bat'
    $signature = & $apksigner verify --verbose --print-certs '.\app\build\outputs\apk\release\app-release.apk' 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Production APK signature verification failed.' }
    $signatureText = $signature -join "`n"
    if ($signatureText -notmatch [regex]::Escape("Signer #1 certificate SHA-256 digest: $($identity.certificateSha256)")) { throw 'Production signing certificate does not match the retained identity.' }
    if ($signatureText -notmatch 'Verified using v2 scheme .*true' -or $signatureText -notmatch 'Verified using v3 scheme .*true') { throw 'Production APK must verify with APK signature schemes v2 and v3.' }
    Write-Output 'Production APK verified: retained certificate, APK signature schemes v2 and v3.'
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    Pop-Location
}
