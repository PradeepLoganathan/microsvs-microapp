# Backend API Testing Guide

This directory contains HTTP request files for testing your microservices backend, both locally and on the Akka Platform.

## Quick Start

### Local Development

1. Start all services:
   ```bash
   ./scripts/run-all.sh
   ```

2. Use `local-development.http` in VS Code REST Client or similar tool:
   - Open the file in VS Code (install REST Client extension)
   - Click "Send Request" on any request
   - Or use curl directly

3. First operation: Seed data
   ```bash
   POST http://localhost:8082/accounts/seed
   ```

### Akka Deployed Environment

1. Use `deployed-akka-platform.http` after exposing services
2. Update the `@baseUrl` variable with your Akka hostname
3. Follow the exposure sequence outlined in the file

## Service Architecture

### Services & Ports (Local)

| Service | Port | Purpose | Depends On |
|---------|------|---------|-----------|
| **statement-service** | 8082 | Transaction history, event sourcing | None |
| **analysis-service** | 8083 | AI analysis of transactions | statement-service |
| **recommendation-service** | 8084 | Product recommendations | analysis-service |
| **product-service** | 8085 | Product catalog | None |

### Dependency Graph

```
Mobile App (Frontend)
    ↓
recommendation-service (8084)
    ↓
analysis-service (8083)
    ↓
statement-service (8082)

product-service (8085) - standalone
```

## API Endpoints Overview

### Statement Service (Foundation Layer)

Base path: `/accounts`

```
GET  /{accountId}/statements              → List all statements
GET  /{accountId}/statements/{statementId} → Get specific statement
GET  /{accountId}/transactions             → All transactions with filtering
POST /{accountId}/statements/{statementId}/transactions → Add transaction
POST /seed                                 → Seed with mock data
```

### Product Service (Catalog)

Base path: `/products`

```
GET  /                    → List all products
GET  /{productId}         → Get product details
POST /{productId}         → Create/update product
POST /seed                → Seed with mock products
```

### Analysis Service (AI-Powered)

Base path: `/accounts`

```
POST /{accountId}/analysis/run          → Run analysis on statement
       ?statementId=stmt-id
       ?mode=heuristic|agent (optional)

GET  /{accountId}/analysis/summary       → Get analysis results
       ?statementId=stmt-id
```

**Modes:**
- **heuristic**: Rule-based categorization (deterministic, always works)
- **agent**: LLM-powered analysis (requires OPENAI_API_KEY)

### Recommendation Service (Frontend Entry Point)

Base path: `/accounts`

```
GET /{accountId}/recommendations          → Get product recommendations
      ?statementId=stmt-id
```

**How it works:**
1. Calls analysis-service to get transaction analysis
2. Analysis-service calls statement-service to get transactions
3. Applies recommendation engine rules
4. Returns relevant product recommendations

## Testing Scenarios

### Scenario 1: Individual Service Testing

**Test each service in isolation:**

```bash
# 1. Statement Service (no dependencies)
curl http://localhost:8082/accounts/acc-1001/statements

# 2. Product Service (no dependencies)
curl http://localhost:8085/products

# 3. Analysis Service (depends on statement-service)
curl -X POST http://localhost:8083/accounts/acc-1001/analysis/run

# 4. Recommendation Service (depends on analysis-service)
curl http://localhost:8084/accounts/acc-1001/recommendations
```

### Scenario 2: Complete Cross-Service Flow

**Test the full request chain:**

```bash
# This single request triggers:
# Client → recommendation-service 
#   → analysis-service 
#     → statement-service
curl http://localhost:8084/accounts/acc-1001/recommendations?statementId=stmt-2025-12
```

### Scenario 3: Data Seeding

**Initialize mock data in order:**

```bash
# 1. Seed statements (foundation)
curl -X POST http://localhost:8082/accounts/seed

# 2. Seed products (optional)
curl -X POST http://localhost:8085/products/seed

# 3. Run analysis (processes statements)
curl -X POST "http://localhost:8083/accounts/acc-1001/analysis/run?statementId=stmt-2025-12"

# 4. Get recommendations (uses analysis results)
curl "http://localhost:8084/accounts/acc-1001/recommendations?statementId=stmt-2025-12"
```

## Akka Platform Deployment

### Which Services to Expose?

When deploying to Akka Platform, not all services need external exposure:

#### Must Expose (for frontend):
- **recommendation-service** - primary API endpoint
- (Optionally) **product-service** - if frontend needs product catalog directly

#### Expose for Backend Communication:
- **analysis-service** - must be accessible to recommendation-service
- **statement-service** - must be accessible to analysis-service

#### Why This Works:
- Backend services use Akka's `HttpClientProvider` for service discovery
- Service-to-service calls happen via internal Akka network
- Only expose routes that external clients need

### Exposure Commands

```bash
# 1. Expose foundation layer
akka services expose statement-service white-bush-8904.gcp-us-east1.akka.services

# 2. Expose analysis layer
akka services expose analysis-service white-bush-8904.gcp-us-east1.akka.services

# 3. Expose recommendation layer (primary frontend API)
akka services expose recommendation-service white-bush-8904.gcp-us-east1.akka.services

# 4. Expose products (if frontend needs it)
akka services expose product-service white-bush-8904.gcp-us-east1.akka.services

# Verify exposed routes
akka services routes list
```

### Removing Exposure

```bash
# Stop exposing a service
akka services unexpose (service-name) (hostname)

# Example (as you already did):
akka services unexpose analysis-service white-bush-8904.gcp-us-east1.akka.services
```

## Using the HTTP Files

### With VS Code REST Client Extension

1. Install REST Client extension (humao.rest-client)
2. Open `local-development.http` or `deployed-akka-platform.http`
3. Click "Send Request" above any request
4. View response in OUTPUT panel

### With curl

```bash
curl -X POST http://localhost:8082/accounts/seed

curl http://localhost:8083/accounts/acc-1001/analysis/summary?statementId=stmt-2025-12

curl http://localhost:8084/accounts/acc-1001/recommendations?statementId=stmt-2025-12
```

### With Postman

1. Import the `.http` file
2. Update collection variables
3. Run requests with "Send" button

## Common Testing Flow

```
1. [OPTIONAL] Clear/reset services (stop and restart)

2. [SETUP] Seed data
   POST http://localhost:8082/accounts/seed

3. [FOUNDATION] Test statement retrieval
   GET http://localhost:8082/accounts/acc-1001/statements

4. [ANALYSIS] Test analysis processing
   POST http://localhost:8083/accounts/acc-1001/analysis/run

5. [VERIFICATION] Check analysis results
   GET http://localhost:8083/accounts/acc-1001/analysis/summary

6. [INTEGRATION] Test full recommendation flow
   GET http://localhost:8084/accounts/acc-1001/recommendations

7. [PRODUCTS] Verify product catalog (optional)
   GET http://localhost:8085/products
```

## Account IDs & Statement IDs

**Default Account ID:** `acc-1001`  
**Default Statement ID:** `stmt-2025-12`

These are seeded with mock data. You can:
- Use different account IDs in requests
- Create custom statements by adding transactions
- Check mock data in `backend/statement-service/src/main/java/com/microapp/statement/api/MockDataProvider.java`

## Troubleshooting

### Service Not Responding
```bash
# Check if services are running
ps aux | grep java

# Check if ports are listening
lsof -i :8082  # statement-service
lsof -i :8083  # analysis-service
lsof -i :8084  # recommendation-service
lsof -i :8085  # product-service
```

### Cross-Service Call Failing
- Verify all dependent services are running
- Check logs: `./scripts/run-all.sh` shows log output
- Verify `analysis.mode` config in `application.conf` (heuristic vs agent)

### Analysis in Agent Mode Requires OpenAI Key
```bash
export OPENAI_API_KEY=sk-...
./scripts/run-all.sh
```

### Deployed Service Timeouts
- Ensure all services are in "Ready" state: `akka services list`
- Check network connectivity to Akka hostname
- Verify correct region is selected: `akka config current-context`

## Performance Notes

- **Statement Service**: Fast, event-sourced queries
- **Analysis Service**: Slower if agent mode (LLM calls), fast if heuristic
- **Recommendation Service**: Chain of 2-3 HTTP calls
- **Products**: Fast, in-memory catalog

For production, consider:
- Caching analysis results
- Reducing HTTP call chains
- Rate limiting public endpoints
