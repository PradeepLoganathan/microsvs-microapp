# build-microapps.ps1 — Build all micro-apps (Windows)

$RootDir = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$MicroappsDir = Join-Path $RootDir "mobile\microapps"

Write-Host ""
Write-Host "Building Micro-Apps..." -ForegroundColor Cyan
Write-Host ""

function Build-MicroApp {
    param(
        [string]$Name,
        [string]$Config = "production"
    )

    Write-Host "  Building $Name (config: $Config)... " -NoNewline -ForegroundColor Cyan

    $dir = Join-Path $MicroappsDir $Name
    Push-Location $dir

    if (-not (Test-Path "node_modules")) {
        npm install --silent 2>$null
    }

    npx ng build --configuration $Config 2>$null

    if ($LASTEXITCODE -eq 0) {
        Write-Host "OK" -ForegroundColor Green
    } else {
        Write-Host "FAILED" -ForegroundColor Red
    }

    Pop-Location
}

Build-MicroApp -Name "statement-details"
Build-MicroApp -Name "statement-analysis" -Config "production"
Build-MicroApp -Name "statement-analysis" -Config "v2"
Build-MicroApp -Name "recommendations"

Write-Host ""
Write-Host "All micro-apps built." -ForegroundColor Green
Write-Host "Next: Run .\scripts\publish-microapps.ps1" -ForegroundColor Cyan
