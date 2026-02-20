# Microservices + Micro-App Banking Demo

A full demo system showcasing microservices on **Akka SDK (Java)** with a mobile banking app built on **Angular 19 + Ionic 8**, delivering independently-updatable micro-app UIs.

## Architecture

![Conceptual Architecture](docs/conceptual-architecture.png)


## Prerequisites

- **Java 21** (Eclipse Adoptium recommended)
- **Apache Maven 3.9+**
- **Node.js 20+** and npm
- **curl** (for testing)
- Akka SDK repository access (configured in `~/.m2/settings.xml`)

## Quick Start

### 1. Start Backend + Platform Services

```bash
# macOS/Linux
./scripts/run-all.sh

# Windows
.\scripts\run-all.ps1
```

This starts all 6 services and prints a summary table.

### 2. Build & Publish Micro-Apps

```bash
# Build all micro-apps (v1 and v2)
./scripts/build-microapps.sh

# Publish to CDN
./scripts/publish-microapps.sh
```

### 3. Start Mobile App

```bash
cd mobile/banking-shell
npm install
npx ionic serve
```

Open `http://localhost:4200` in your browser.

### 4. Stop Everything

```bash
./scripts/stop-all.sh
```

## Service Ports

| Service | Port | Description |
|---------|------|-------------|
| statement-service | 8082 | Mock statements & transactions |
| analysis-service | 8083 | AI agent transaction analysis |
| recommendation-service | 8084 | Rules-based product recommendations |
| product-service | 8085 | Mock product catalog |
| microfrontend-registry | 8079 | Micro-app manifest server |
| microfrontend-cdn | 8090 | Static micro-app bundle server |
| mobile banking-shell | 4200 | Ionic Angular host app |

## API Endpoints

```bash
# Statements
curl http://localhost:8082/accounts/acc-1001/statements
curl http://localhost:8082/accounts/acc-1001/statements/stmt-2025-12

# Analysis
curl -X POST "http://localhost:8083/accounts/acc-1001/analysis/run?statementId=stmt-2025-12"
curl "http://localhost:8083/accounts/acc-1001/analysis/summary?statementId=stmt-2025-12"

# Products
curl http://localhost:8085/products

# Recommendations
curl "http://localhost:8084/accounts/acc-1001/recommendations?statementId=stmt-2025-12"

# Manifest
curl "http://localhost:8079/microfrontends/manifest?channel=demo"
```

## Key Demo: Micro-App Live Update (v1 → v2)

1. With everything running, open the mobile app and go to **Statement Analysis** tab
2. You see **v1** — basic category totals table
3. Edit `platform/microfrontend-registry/manifest.json`:
   - Change `StatementAnalysis` version from `1.0.0` to `2.0.0`
   - Change its `remoteEntry` URL from `.../1.0.0/main.js` to `.../2.0.0/main.js`
4. Refresh the browser
5. Statement Analysis now shows **v2** — with top merchants section and "v2" badge
6. **Backend was never restarted** — only the frontend bundle was swapped

## Agentic AI: analysis-service

The analysis-service demonstrates the **Akka Agent** construct with function tools:

- `TransactionAnalysisAgent` extends `Agent` with `@FunctionTool` methods
- Tools: `fetchTransactions`, `categorizeTransaction`, `persistAnalysis`
- Default mode: **heuristic** (deterministic, no LLM needed)
- Agent mode: Set `ANALYSIS_MODE=agent` and `OPENAI_API_KEY` env vars

## Project Structure

```
backend/
  statement-service/     Akka SDK — mock statement data
  analysis-service/      Akka SDK — Agent + heuristic categorizer
  recommendation-service/ Akka SDK — rules-based recommendations
  product-service/       Akka SDK — mock product catalog
platform/
  microfrontend-registry/  Manifest JSON server
  microfrontend-cdn/       Static file server for micro-app bundles
mobile/
  banking-shell/           Ionic Angular host app
  microapps/
    statement-details/     Web Component micro-app
    statement-analysis/    Web Component micro-app (v1 + v2)
    recommendations/       Web Component micro-app
scripts/
  run-all.sh / .ps1        Start all services
  stop-all.sh / .ps1       Stop all services
  build-microapps.sh / .ps1  Build micro-app bundles
  publish-microapps.sh / .ps1  Publish to CDN
```

## Troubleshooting

- **Port already in use**: Run `./scripts/stop-all.sh` first, or check with `lsof -i :PORT`
- **Service won't start**: Check `logs/<service>.log` for errors
- **Maven auth errors**: Ensure Akka SDK repo is configured in `~/.m2/settings.xml`
- **Clean start**: `./scripts/run-all.sh --clean` wipes logs and PID files
