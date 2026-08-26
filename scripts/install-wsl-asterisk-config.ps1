param([switch] $InstallMobileConfig)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:ASTERISK_ARI_USER) -or
    [string]::IsNullOrWhiteSpace($env:ASTERISK_ARI_PASSWORD)) {
    throw 'Set ASTERISK_ARI_USER and ASTERISK_ARI_PASSWORD first.'
}
if ($env:ASTERISK_ARI_USER -notmatch '^[A-Za-z0-9_-]+$') {
    throw 'ASTERISK_ARI_USER contains unsupported characters.'
}
if ($env:ASTERISK_ARI_PASSWORD -match "[`r`n]") {
    throw 'ASTERISK_ARI_PASSWORD must be one line.'
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$configRoot = Join-Path $projectRoot 'infra\asterisk-wsl'
$promptRoot = Join-Path $projectRoot 'prompts'
$stagingRoot = '/tmp/waihu-asterisk-config'

wsl -d Ubuntu-22.04 -u root -- mkdir -p $stagingRoot
foreach ($name in @('http.conf', 'extensions.conf')) {
    $source = (Join-Path $configRoot $name) -replace '\\', '/'
    wsl -d Ubuntu-22.04 -u root -- cp "$(wsl -d Ubuntu-22.04 -- wslpath -a $source)" "$stagingRoot/$name"
}

$ariTemplate = Get-Content -Raw (Join-Path $configRoot 'ari.conf')
$ariRendered = $ariTemplate.Replace('__ARI_USER__', $env:ASTERISK_ARI_USER).Replace('__ARI_PASSWORD__', $env:ASTERISK_ARI_PASSWORD)
$ariRendered | wsl -d Ubuntu-22.04 -u root -- tee "$stagingRoot/ari.conf" | Out-Null

if ($InstallMobileConfig) {
    if ([string]::IsNullOrWhiteSpace($env:BLUETOOTH_ADAPTER_ADDRESS) -or
        [string]::IsNullOrWhiteSpace($env:PHONE_BLUETOOTH_ADDRESS)) {
        throw 'Set BLUETOOTH_ADAPTER_ADDRESS and PHONE_BLUETOOTH_ADDRESS first.'
    }
    $mobileTemplate = Get-Content -Raw (Join-Path $configRoot 'chan_mobile.conf')
    $mobileRendered = $mobileTemplate.Replace('__BLUETOOTH_ADAPTER_ADDRESS__', $env:BLUETOOTH_ADAPTER_ADDRESS).Replace('__PHONE_BLUETOOTH_ADDRESS__', $env:PHONE_BLUETOOTH_ADDRESS)
    $mobileRendered | wsl -d Ubuntu-22.04 -u root -- tee "$stagingRoot/chan_mobile.conf" | Out-Null
}

$files = @('http.conf', 'ari.conf', 'extensions.conf')
if ($InstallMobileConfig) { $files += 'chan_mobile.conf' }
foreach ($name in $files) {
    wsl -d Ubuntu-22.04 -u root -- bash -lc "test -e /etc/asterisk/$name.waihu-backup || cp -a /etc/asterisk/$name /etc/asterisk/$name.waihu-backup; install -o asterisk -g asterisk -m 0640 $stagingRoot/$name /etc/asterisk/$name"
}

$soundRoot = '/usr/share/asterisk/sounds/custom'
wsl -d Ubuntu-22.04 -u root -- mkdir -p $soundRoot
foreach ($prompt in @('identity-question.wav', 'payment-question.wav', 'closing.wav')) {
    $promptPath = Join-Path $promptRoot $prompt
    if (-not (Test-Path -LiteralPath $promptPath)) { throw "Missing prompt: $promptPath" }
    $source = $promptPath -replace '\\', '/'
    $wslSource = wsl -d Ubuntu-22.04 -- wslpath -a $source
    wsl -d Ubuntu-22.04 -u root -- install -o asterisk -g asterisk -m 0644 $wslSource "$soundRoot/$prompt"
    wsl -d Ubuntu-22.04 -u root -- test -r "$soundRoot/$prompt"
    if ($LASTEXITCODE -ne 0) { throw "Asterisk cannot read prompt: $prompt" }
}

wsl -d Ubuntu-22.04 -u root -- hciconfig hci0 voice 0x0060
wsl -d Ubuntu-22.04 -u root -- systemctl restart asterisk
Start-Sleep -Seconds 2
& (Join-Path $PSScriptRoot 'check-wsl-mobile.ps1')
