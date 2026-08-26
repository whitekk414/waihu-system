$ErrorActionPreference = 'Stop'

function Confirm-Check([string] $Name, [scriptblock] $Action) {
    & $Action
    if ($LASTEXITCODE -ne 0) { throw "$Name failed (exit $LASTEXITCODE)" }
    Write-Host "[OK] $Name"
}

if ([string]::IsNullOrWhiteSpace($env:ASTERISK_ARI_USER) -or
    [string]::IsNullOrWhiteSpace($env:ASTERISK_ARI_PASSWORD)) {
    throw 'Set ASTERISK_ARI_USER and ASTERISK_ARI_PASSWORD first.'
}

$usbipd = (Get-Command usbipd -ErrorAction SilentlyContinue).Source
if (-not $usbipd) {
    $usbipd = Join-Path $env:ProgramFiles 'usbipd-win\usbipd.exe'
}
if (-not (Test-Path -LiteralPath $usbipd)) { throw 'usbipd-win is not installed.' }
$usb = & $usbipd list | Out-String
if ($usb -notmatch '(?m)^\S+\s+0a12:0001\s+.*\s+(Shared|Attached)\s*$') {
    throw 'CSR8510 (0a12:0001) is not shared/attached to WSL.'
}
Write-Host '[OK] CSR8510 shared with WSL'

Confirm-Check 'BlueZ active' {
    wsl -d Ubuntu-22.04 -u root -- systemctl is-active --quiet bluetooth
}

$modules = wsl -d Ubuntu-22.04 -u root -- asterisk -rx 'module show like mobile' | Out-String
if ($modules -notmatch 'chan_mobile\.so\s+Bluetooth Mobile Device Channel Driver\s+\d+\s+Running') {
    throw 'chan_mobile is not running.'
}
Write-Host '[OK] chan_mobile running'

$mobile = wsl -d Ubuntu-22.04 -u root -- asterisk -rx 'mobile show devices' | Out-String
if ($mobile -notmatch '(?m)^honor\s+.*Yes\s+Free\s+No\s*$') {
    throw "HONOR mobile is not connected/free.`n$mobile"
}
Write-Host '[OK] HONOR connected and free'

$credential = "$($env:ASTERISK_ARI_USER):$($env:ASTERISK_ARI_PASSWORD)"
curl.exe -fsS -u $credential 'http://127.0.0.1:8088/ari/asterisk/info' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'ARI authentication failed.' }
Write-Host '[OK] ARI authenticated'
