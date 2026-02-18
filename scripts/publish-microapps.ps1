# publish-microapps.ps1 — Publish built micro-apps to CDN (Windows)

$RootDir = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$MicroappsDir = Join-Path $RootDir "mobile\microapps"
$CdnDir = Join-Path $RootDir "platform\microfrontend-cdn\www"

Write-Host ""
Write-Host "Publishing Micro-Apps to CDN..." -ForegroundColor Cyan
Write-Host ""

function Publish-MicroApp {
    param(
        [string]$Name,
        [string]$Version,
        [string]$SourceDir
    )

    $targetDir = Join-Path $CdnDir "$Name\$Version"
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

    Write-Host "  Publishing $Name v$Version... " -NoNewline -ForegroundColor Cyan

    if (-not (Test-Path $SourceDir)) {
        Write-Host "FAILED (build output not found)" -ForegroundColor Red
        return
    }

    Copy-Item "$SourceDir\*.js" $targetDir -Force -ErrorAction SilentlyContinue
    Copy-Item "$SourceDir\*.css" $targetDir -Force -ErrorAction SilentlyContinue

    if (-not (Test-Path (Join-Path $targetDir "main.js"))) {
        $largest = Get-ChildItem "$targetDir\*.js" | Sort-Object Length -Descending | Select-Object -First 1
        if ($largest) {
            Copy-Item $largest.FullName (Join-Path $targetDir "main.js")
        }
    }

    Write-Host "OK" -ForegroundColor Green
}

Publish-MicroApp -Name "statement-details" -Version "1.0.0" `
    -SourceDir (Join-Path $MicroappsDir "statement-details\dist\statement-details\browser")

Publish-MicroApp -Name "statement-analysis" -Version "1.0.0" `
    -SourceDir (Join-Path $MicroappsDir "statement-analysis\dist\statement-analysis\browser")

Publish-MicroApp -Name "statement-analysis" -Version "2.0.0" `
    -SourceDir (Join-Path $MicroappsDir "statement-analysis\dist\statement-analysis-v2\browser")

Publish-MicroApp -Name "recommendations" -Version "1.0.0" `
    -SourceDir (Join-Path $MicroappsDir "recommendations\dist\recommendations\browser")

Write-Host ""
Write-Host "All micro-apps published." -ForegroundColor Green
Write-Host ""
