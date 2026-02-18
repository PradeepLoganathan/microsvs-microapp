# stop-all.ps1 — Stop all services (Windows)

$RootDir = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$RunDir = Join-Path $RootDir "run"

Write-Host ""
Write-Host "Stopping all services..." -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path $RunDir)) {
    Write-Host "No run directory found. Nothing to stop." -ForegroundColor Red
    exit 0
}

$stopped = 0
Get-ChildItem -Path $RunDir -Filter "*.pid" | ForEach-Object {
    $svcName = $_.BaseName
    $pid = Get-Content $_.FullName

    Write-Host "  Stopping $svcName (PID: $pid)... " -NoNewline -ForegroundColor Cyan

    try {
        $proc = Get-Process -Id $pid -ErrorAction SilentlyContinue
        if ($proc) {
            # Kill process tree
            Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
            Write-Host "stopped" -ForegroundColor Green
            $stopped++
        } else {
            Write-Host "already stopped" -ForegroundColor Red
        }
    } catch {
        Write-Host "already stopped" -ForegroundColor Red
    }

    Remove-Item $_.FullName -Force
}

if ($stopped -eq 0) {
    Write-Host "  No running services found." -ForegroundColor Red
}

Write-Host ""
Write-Host "All services stopped." -ForegroundColor Green
Write-Host ""
