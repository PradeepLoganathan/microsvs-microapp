# End-to-End Demo Runbook

This runbook covers deploying all services to Akka Platform and running the banking shell locally against them.

## Prerequisites

- `akka` CLI installed and authenticated
- `node` / `npm` installed (for building micro-apps and shell)
- `mvn` installed (for building Java services)
- All backend and platform source code compiled at least once

---

## Phase 1: Build Deployed Micro-App Bundles

The bundles must be compiled with the `deployed` config so they call the Akka Platform backend URLs instead of localhost.

```bash
# Statement Details (v1)
cd mobile/microapps/statement-details
npx ng build --configuration deployed

# Statement Analysis (v1 + v2)
cd mobile/microapps/statement-analysis
npx ng build --configuration deployed          # v1
cp dist/browser/main.js /tmp/analysis-v1.js    # save v1 before v2 overwrites
npx ng build --configuration deployed-v2       # v2

# Recommendations (v1)
cd mobile/microapps/recommendations
npx ng build --configuration deployed
```

### Copy Bundles into Platform Service Resources

```bash
# From the project root
cp mobile/microapps/statement-details/dist/browser/main.js \
   platform/platform-service/src/main/resources/www/statement-details/1.0.0/main.js

cp /tmp/analysis-v1.js \
   platform/platform-service/src/main/resources/www/statement-analysis/1.0.0/main.js

cp mobile/microapps/statement-analysis/dist/browser/main.js \
   platform/platform-service/src/main/resources/www/statement-analysis/2.0.0/main.js

cp mobile/microapps/recommendations/dist/browser/main.js \
   platform/platform-service/src/main/resources/www/recommendations/1.0.0/main.js
```

---

## Phase 2: Build and Deploy All Services

### Build Service Docker Images

`mvn install` builds the JAR and creates a Docker image via the Fabric8 docker-maven-plugin (inherited from `akka-javasdk-parent`).

```bash
mvn -f backend/statement-service/pom.xml clean install -DskipTests
mvn -f backend/product-service/pom.xml clean install -DskipTests
mvn -f backend/analysis-service/pom.xml clean install -DskipTests
mvn -f backend/recommendation-service/pom.xml clean install -DskipTests
mvn -f platform/platform-service/pom.xml clean install -DskipTests
```

### Deploy to Akka Platform

The `--push` flag pushes the local Docker image to the Akka container registry.

```bash
akka service deploy statement-service statement-service:latest --push
akka service deploy product-service product-service:latest --push
akka service deploy analysis-service analysis-service:latest --push
akka service deploy recommendation-service recommendation-service:latest --push
akka service deploy platform-service platform-service:latest --push
```

---

## Phase 3: Expose Services

All services share a single hostname with URI prefixes:

```bash
akka services expose statement-service \
  --hostname banking-microapp.gcp-us-east1.akka.services \
  --uri-prefix /stmt --enable-cors

akka services expose product-service \
  --hostname banking-microapp.gcp-us-east1.akka.services \
  --uri-prefix /prod --enable-cors

akka services expose analysis-service \
  --hostname banking-microapp.gcp-us-east1.akka.services \
  --uri-prefix /anls --enable-cors

akka services expose recommendation-service \
  --hostname banking-microapp.gcp-us-east1.akka.services \
  --uri-prefix /recs --enable-cors

akka services expose platform-service \
  --hostname banking-microapp.gcp-us-east1.akka.services \
  --uri-prefix /platform --enable-cors
```

---

## Phase 4: Seed Data

```bash
# Seed backend services with demo data
curl -X POST https://banking-microapp.gcp-us-east1.akka.services/stmt/accounts/seed
curl -X POST https://banking-microapp.gcp-us-east1.akka.services/prod/products/seed

# Seed the micro-frontend manifest
curl -X POST https://banking-microapp.gcp-us-east1.akka.services/platform/microfrontends/seed

# Verify manifest is seeded
curl https://banking-microapp.gcp-us-east1.akka.services/platform/microfrontends/manifest/demo
```

---

## Phase 5: Run the Shell Locally

```bash
cd mobile/banking-shell
ng serve --configuration deployed
```

Open `http://localhost:4200` in the browser.

The shell fetches the manifest from Akka Platform, loads bundles from Akka Platform, and micro-apps call the deployed backend services.

---

## Phase 6: Live Version Swap Demo

Swap `statement-analysis` from v1 to v2 with a single API call — no restart, no rebuild, no redeployment:

```bash
curl -X PUT https://banking-microapp.gcp-us-east1.akka.services/platform/microfrontends/manifest/demo \
  -H "Content-Type: application/json" \
  -d '{
    "manifestVersion": "2026-02-20T01:00:00Z",
    "channel": "demo",
    "microfrontends": [
      {
        "name": "statement-details",
        "version": "1.0.0",
        "remoteEntry": "/bundles/statement-details/1.0.0/main.js",
        "exposedModule": "./Module",
        "elementTag": "mf-statement-details"
      },
      {
        "name": "statement-analysis",
        "version": "2.0.0",
        "remoteEntry": "/bundles/statement-analysis/2.0.0/main.js",
        "exposedModule": "./Module",
        "elementTag": "mf-statement-analysis"
      },
      {
        "name": "recommendations",
        "version": "1.0.0",
        "remoteEntry": "/bundles/recommendations/1.0.0/main.js",
        "exposedModule": "./Module",
        "elementTag": "mf-recommendations"
      }
    ]
  }'
```

Navigate to the Analysis tab in the shell — v2 loads with the teal theme, top merchants section, and a "v2" badge.

### Roll Back to v1

```bash
curl -X PUT https://banking-microapp.gcp-us-east1.akka.services/platform/microfrontends/manifest/demo \
  -H "Content-Type: application/json" \
  -d '{
    "manifestVersion": "2026-02-20T02:00:00Z",
    "channel": "demo",
    "microfrontends": [
      {
        "name": "statement-details",
        "version": "1.0.0",
        "remoteEntry": "/bundles/statement-details/1.0.0/main.js",
        "exposedModule": "./Module",
        "elementTag": "mf-statement-details"
      },
      {
        "name": "statement-analysis",
        "version": "1.0.0",
        "remoteEntry": "/bundles/statement-analysis/1.0.0/main.js",
        "exposedModule": "./Module",
        "elementTag": "mf-statement-analysis"
      },
      {
        "name": "recommendations",
        "version": "1.0.0",
        "remoteEntry": "/bundles/recommendations/1.0.0/main.js",
        "exposedModule": "./Module",
        "elementTag": "mf-recommendations"
      }
    ]
  }'
```

---

## Quick Reference

### Service Endpoints

| Service | URI Prefix | Full URL |
|---------|-----------|----------|
| Statement | `/stmt` | `https://banking-microapp.gcp-us-east1.akka.services/stmt/...` |
| Product | `/prod` | `https://banking-microapp.gcp-us-east1.akka.services/prod/...` |
| Analysis | `/anls` | `https://banking-microapp.gcp-us-east1.akka.services/anls/...` |
| Recommendation | `/recs` | `https://banking-microapp.gcp-us-east1.akka.services/recs/...` |
| Platform | `/platform` | `https://banking-microapp.gcp-us-east1.akka.services/platform/...` |

### Key Platform Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/platform/microfrontends/seed` | Seed default manifest |
| `GET` | `/platform/microfrontends/manifest/demo` | Fetch current manifest |
| `PUT` | `/platform/microfrontends/manifest/demo` | Update manifest (version swap) |
| `GET` | `/platform/bundles/{name}/{ver}/main.js` | Fetch a micro-app bundle |

### Build Configurations

| Config | Target | Use |
|--------|--------|-----|
| `production` | Local (localhost URLs) | `ng build --configuration production` |
| `deployed` | Akka Platform URLs | `ng build --configuration deployed` |
| `v2` | Local v2 (statement-analysis) | `ng build --configuration v2` |
| `deployed-v2` | Deployed v2 (statement-analysis) | `ng build --configuration deployed-v2` |
| `development` | Local dev server | `ng serve` (default) |

### Running the Shell

| Scenario | Command |
|----------|---------|
| Local backend (all on localhost) | `ng serve` |
| Deployed backend (Akka Platform) | `ng serve --configuration deployed` |
