# Run locally. Secret input is hidden and never written into the repository.
param([switch]$CreateSigningIdentity, [switch]$SetAmazonSecret, [switch]$UploadSecrets)
$ErrorActionPreference = 'Stop'
$directory = Join-Path $env:USERPROFILE '.codex\signing\trailnest-billing-production'
$privatePath = Join-Path $directory 'private-key.dpapi'
$amazonPath = Join-Path $directory 'amazon-rvs.dpapi'
$root = Split-Path $PSScriptRoot -Parent
if (!(Test-Path -LiteralPath $directory)) {
    New-Item -ItemType Directory -Path $directory | Out-Null
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls $directory /inheritance:r /grant:r "${identity}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not restrict the production secret directory.' }
}
if ($CreateSigningIdentity) {
    $publicPath = Join-Path $root 'app\src\release\assets\billing-verifier-public.pem'
    if ((Test-Path -LiteralPath $privatePath) -or (Test-Path -LiteralPath $publicPath)) { throw 'Production signing identity already exists; restore its protected key rather than rotating it.' }
    $rsa = [Security.Cryptography.RSA]::Create(2048)
    try {
        $pem = $rsa.ExportPkcs8PrivateKeyPem()
        $encrypted = ConvertFrom-SecureString (ConvertTo-SecureString $pem -AsPlainText -Force)
        [IO.File]::WriteAllText($privatePath, $encrypted)
        $publicDirectory = Join-Path $root 'app\src\release\assets'
        New-Item -ItemType Directory -Force -Path $publicDirectory | Out-Null
        [IO.File]::WriteAllText((Join-Path $publicDirectory 'billing-verifier-public.pem'), $rsa.ExportSubjectPublicKeyInfoPem() + "`n")
        Write-Output 'Dedicated production verifier identity retained with current-user DPAPI; public Android pin created.'
    } finally { $rsa.Dispose(); $pem = $null; $encrypted = $null }
}
if ($SetAmazonSecret) {
    $secret = Read-Host 'Amazon RVS shared secret (hidden; from the Developer Console, not an account password)' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try {
        $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        if ([string]::IsNullOrWhiteSpace($plain) -or $plain.Length -gt 2048 -or $plain -match '\s') { throw 'Invalid secret shape; nothing saved.' }
        [IO.File]::WriteAllText($amazonPath, (ConvertFrom-SecureString $secret))
        Write-Output 'Amazon secret retained outside Git with current-user DPAPI.'
    } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $plain = $null }
}
if ($UploadSecrets) {
    if (!(Test-Path -LiteralPath $privatePath) -or !(Test-Path -LiteralPath $amazonPath)) { throw 'Configure the production signing identity and Amazon secret first.' }
    $pointers = @()
    $previousMetrics = $env:WRANGLER_SEND_METRICS
    try {
        $values = @{}
        foreach ($entry in @(@('BILLING_SIGNING_KEY_BASE64', $privatePath), @('AMAZON_RVS_SHARED_SECRET', $amazonPath))) {
            $secure = (Get-Content -LiteralPath $entry[1] -Raw).Trim() | ConvertTo-SecureString
            $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure); $pointers += $pointer
            $plain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
            $values[$entry[0]] = if ($entry[0] -eq 'BILLING_SIGNING_KEY_BASE64') { [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($plain)) } else { $plain }
        }
        $privateKey = [Security.Cryptography.RSA]::Create()
        try {
            $privateKey.ImportFromPem([Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($values.BILLING_SIGNING_KEY_BASE64)))
            $fingerprint = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($privateKey.ExportSubjectPublicKeyInfo())).ToLowerInvariant()
            $configuration = Get-Content -LiteralPath (Join-Path $root 'config\billing\production.json') -Raw | ConvertFrom-Json
            if ($fingerprint -ne $configuration.publicKeySpkiSha256) { throw 'Protected key does not match the retained production identity; refusing upload.' }
        } finally { $privateKey.Dispose() }
        $env:WRANGLER_SEND_METRICS = 'false'
        $values | ConvertTo-Json -Compress | & npx --yes wrangler@4.156.0 secret bulk --config (Join-Path $root 'billing-server\wrangler.jsonc')
        if ($LASTEXITCODE -ne 0) { throw 'Cloudflare secret upload failed.' }
    } finally {
        foreach ($pointer in $pointers) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
        $plain = $null; $values = $null; $env:WRANGLER_SEND_METRICS = $previousMetrics
    }
}
