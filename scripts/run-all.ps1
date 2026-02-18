# run-all.ps1 — Start all microservices and platform services (Windows)

param(
    [switch]$Clean
)

$ErrorActionPreference = "Continue"
$RootDir = Split-Path -Parent (Split-Path -Parent $PSCommandPath)
$LogsDir = Join-Path $RootDir "logs"
$RunDir = Join-Path $RootDir "run"

if ($Clean) {
    Write-Host "Cleaning logs and run directories..." -ForegroundColor Yellow
    Remove-Item -Recurse -Force $LogsDir -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force $RunDir -ErrorAction SilentlyContinue
    Write-Host "Clean complete." -ForegroundColor Green
    exit 0
}

New-Item -ItemType Directory -Force -Path $LogsDir | Out-Null
New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

function Test-Port {
    param([int]$Port)
    try {
        $listener = [System.Net.Sockets.TcpClient]::new()
        $listener.Connect("localhost", $Port)
        $listener.Close()
        return $false  # Port in use
    } catch {
        return $true   # Port available
    }
}

function Start-AkkaService {
    param(
        [string]$Name,
        [int]$Port,
        [string]$Dir
    )

    Write-Host "  Starting $Name on port $Port... " -NoNewline -ForegroundColor Cyan

    if (-not (Test-Port $Port)) {
        Write-Host "FAILED (port $Port already in use)" -ForegroundColor Red
        return
    }

    $fullDir = Join-Path $RootDir $Dir
    $logFile = Join-Path $LogsDir "$Name.log"

    $proc = Start-Process -FilePath "mvn" -ArgumentList "-q", "compile", "exec:java" `
        -WorkingDirectory $fullDir `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError (Join-Path $LogsDir "$Name.err.log") `
        -PassThru -WindowStyle Hidden

    Set-Content -Path (Join-Path $RunDir "$Name.pid") -Value $proc.Id

    # Wait for service
    $retries = 60
    while ($retries -gt 0) {
        if ($proc.HasExited) {
            Write-Host "FAILED (process died)" -ForegroundColor Red
            return
        }
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:$Port" -Method GET -TimeoutSec 1 -ErrorAction SilentlyContinue
            if ($response.StatusCode -ge 0) {
                Write-Host "OK (PID: $($proc.Id))" -ForegroundColor Green
                return
            }
        } catch { }
        Start-Sleep -Seconds 1
        $retries--
    }

    Write-Host "TIMEOUT (PID: $($proc.Id))" -ForegroundColor Yellow
}

function Start-PlatformService {
    param(
        [string]$Name,
        [int]$Port,
        [string]$Dir
    )

    Write-Host "  Starting $Name on port $Port... " -NoNewline -ForegroundColor Cyan

    if (-not (Test-Port $Port)) {
        Write-Host "FAILED (port $Port already in use)" -ForegroundColor Red
        return
    }

    $fullDir = Join-Path $RootDir $Dir
    $logFile = Join-Path $LogsDir "$Name.log"

    $proc = Start-Process -FilePath "mvn" -ArgumentList "-q", "compile", "exec:java" `
        -WorkingDirectory $fullDir `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError (Join-Path $LogsDir "$Name.err.log") `
        -PassThru -WindowStyle Hidden

    Set-Content -Path (Join-Path $RunDir "$Name.pid") -Value $proc.Id

    $retries = 15
    while ($retries -gt 0) {
        try {
            Invoke-WebRequest -Uri "http://localhost:$Port/health" -TimeoutSec 1 -ErrorAction SilentlyContinue | Out-Null
            Write-Host "OK (PID: $($proc.Id))" -ForegroundColor Green
            return
        } catch { }
        Start-Sleep -Seconds 1
        $retries--
    }

    Write-Host "TIMEOUT (PID: $($proc.Id))" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "  Microservices + Micro-App Demo — Starting All" -ForegroundColor Cyan
Write-Host ""

Write-Host "Starting backend services (Akka SDK)..." -ForegroundColor Cyan
Start-AkkaService -Name "statement-service" -Port 8082 -Dir "backend/statement-service"
Start-AkkaService -Name "product-service" -Port 8085 -Dir "backend/product-service"
Start-AkkaService -Name "analysis-service" -Port 8083 -Dir "backend/analysis-service"
Start-AkkaService -Name "recommendation-service" -Port 8084 -Dir "backend/recommendation-service"

Write-Host ""
Write-Host "Seeding data into EventSourced entities..." -ForegroundColor Cyan
Start-Sleep -Seconds 2

Write-Host "  Seeding statement-service... " -NoNewline -ForegroundColor Cyan
try {
    Invoke-WebRequest -Uri "http://localhost:8082/accounts/seed" -Method POST -TimeoutSec 10 -ErrorAction SilentlyContinue | Out-Null
    Write-Host "OK" -ForegroundColor Green
} catch {
    Write-Host "SKIPPED (service may not be ready)" -ForegroundColor Yellow
}

Write-Host "  Seeding product-service... " -NoNewline -ForegroundColor Cyan
try {
    Invoke-WebRequest -Uri "http://localhost:8085/products/seed" -Method POST -TimeoutSec 10 -ErrorAction SilentlyContinue | Out-Null
    Write-Host "OK" -ForegroundColor Green
} catch {
    Write-Host "SKIPPED (service may not be ready)" -ForegroundColor Yellow
}

Start-Sleep -Seconds 1

Write-Host ""
Write-Host "Starting platform services..." -ForegroundColor Cyan
Start-PlatformService -Name "microfrontend-registry" -Port 8079 -Dir "platform/microfrontend-registry"
Start-PlatformService -Name "microfrontend-cdn" -Port 8090 -Dir "platform/microfrontend-cdn"

Write-Host ""
Write-Host "Service Summary:" -ForegroundColor Cyan
Write-Host "  statement-service       : http://localhost:8082"
Write-Host "  product-service         : http://localhost:8085"
Write-Host "  analysis-service        : http://localhost:8083"
Write-Host "  recommendation-service  : http://localhost:8084"
Write-Host "  microfrontend-registry  : http://localhost:8079"
Write-Host "  microfrontend-cdn       : http://localhost:8090"
Write-Host ""
Write-Host "Logs: $LogsDir" -ForegroundColor Cyan
Write-Host ""
