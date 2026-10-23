param([string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe", [string]$OutputDirectory = "$PSScriptRoot\..\artifacts\android-captures")
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Adb)) { throw 'ADB not found. Supply -Adb with the Android SDK adb.exe path.' }
$devices = & $Adb devices
if (-not ($devices | Select-String '\sdevice$')) { throw 'Connect one unlocked test device or start a working emulator. No screenshots have been generated.' }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
& $Adb shell am start -n com.trailnest.app/.MainActivity
Write-Host 'Navigate on the device to the implemented screen to capture. Each capture is raw QA evidence, not a marketing screenshot.'
$name = Read-Host 'Screen name (letters, digits, hyphens)'
if ($name -notmatch '^[a-zA-Z0-9-]+$') { throw 'Invalid screenshot name.' }
& $Adb shell screencap -p /sdcard/trailnest-qa.png
& $Adb pull /sdcard/trailnest-qa.png (Join-Path $OutputDirectory "$name.png")
if ($LASTEXITCODE -ne 0) { throw 'Screenshot capture failed.' }
& $Adb shell rm /sdcard/trailnest-qa.png
$manifest = @{ capturedAt = (Get-Date).ToUniversalTime().ToString('o'); file = "$name.png"; locale = (& $Adb shell getprop persist.sys.locale); marketingApproved = $false; actualDeviceCapture = $true }
$manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $OutputDirectory "$name.json") -Encoding utf8
Write-Host "Saved real device capture: $name.png. Check readability and dimensions before any listing use."
