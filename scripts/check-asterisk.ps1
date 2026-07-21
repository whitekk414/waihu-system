$ErrorActionPreference = 'Stop'

$composePath = Join-Path $PSScriptRoot '..\docker-compose.yml'
if (-not (Test-Path -LiteralPath $composePath)) {
    throw "Missing docker-compose.yml"
}

$compose = Get-Content -Raw -LiteralPath $composePath
foreach ($port in @('5060:5060/udp', '10000-10100:10000-10100/udp', '8088:8088')) {
    if (-not $compose.Contains($port)) {
        throw "Missing required port mapping: $port"
    }
}

$ariUser = if ($env:ASTERISK_ARI_USER) { $env:ASTERISK_ARI_USER } else { 'outbound' }
$ariPassword = if ($env:ASTERISK_ARI_PASSWORD) { $env:ASTERISK_ARI_PASSWORD } else { 'outbound-dev-only' }
$pair = "${ariUser}:${ariPassword}"
$auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
$response = Invoke-RestMethod -Uri 'http://localhost:8088/ari/asterisk/info' -Headers @{ Authorization = "Basic $auth" } -TimeoutSec 5
if (-not $response.system.version) {
    throw 'ARI response did not include Asterisk version'
}

Write-Output "Asterisk ARI healthy: $($response.system.version)"
