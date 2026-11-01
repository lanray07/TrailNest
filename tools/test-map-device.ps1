# Runs only the isolated draft's explicit provider acceptance test over USB.
param([ValidateSet('download','interrupt','offline')][string]$Phase = 'download')
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if ((& $adb -d get-state 2>$null).Trim() -ne 'device') { throw 'One authorized USB device is required.' }
$outputDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) 'artifacts/reports/fire-map-2026-11-01'
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
$outputPath = Join-Path $outputDirectory "map-$Phase-current.txt"
$restoreWifi = $false
try {
    & $adb -d shell am force-stop com.trailnest.app.draft
    if ($LASTEXITCODE -ne 0) { throw 'Draft force-stop failed.' }
    $draftProcess = & $adb -d shell pidof com.trailnest.app.draft
    if ($draftProcess) { throw 'Draft process must be stopped before acceptance.' }
    if ($Phase -eq 'offline') {
        if ((& $adb -d shell settings get global wifi_on).Trim() -ne '1') { throw 'Expected original Wi-Fi enabled; refusing to change unknown networking.' }
        $restoreWifi = $true
        & $adb -d shell svc wifi disable
        if ($LASTEXITCODE -ne 0) { throw 'Wi-Fi disconnect failed.' }
    }
    $arguments = @('-d','shell','am','instrument','-w','-e','class','com.trailnest.app.MapProviderDeviceTest','-e','mapProviderAcceptance','true','-e','mapPhase',$Phase)
    if ($restoreWifi) { $arguments += @('-e','restoreWifiAfter','true') }
    $arguments += 'com.trailnest.app.draft.test/androidx.test.runner.AndroidJUnitRunner'
    $result = & $adb @arguments 2>&1
    $result | Set-Content -LiteralPath $outputPath -Encoding utf8
    $result | Select-Object -Last 20
    if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \(1 test\)' -or ($result -join "`n") -match 'FAILURES!!!') { throw "Map $Phase acceptance failed; see $outputPath" }
} finally {
    if ($restoreWifi) {
        & $adb -d shell svc wifi enable
        if ($LASTEXITCODE -ne 0 -or (& $adb -d shell settings get global wifi_on).Trim() -ne '1') { throw 'Could not confirm original Wi-Fi restored; restore it on the device.' }
        Write-Output 'Original Wi-Fi restored.'
    }
}
