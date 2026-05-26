# Running the Demo Locally

How to start the 5 backend services, seed data, and launch the UI — for local
development and demos.

## Prerequisites
- **Java 21**, **Maven 3.9+**, **Node 20+**, **curl**
- Free ports: `8079`, `8082`, `8083`, `8084`, `8085`, `4200`

Test data is fixed: account **`acc-1001`**, statement **`stmt-2025-12`**.

---

## TL;DR — one command

```bash
# from the repo root
./scripts/run-local.sh            # start all 5 services (right order), seed, smoke-verify
                                  #   add --clean for a fresh build
cd mobile/banking-shell && npm install && npm start   # UI on http://localhost:4200

# stop everything later:
./scripts/stop-local.sh
```

The rest of this doc is the **manual** version — useful to understand the system
or when you want each service in its own terminal tab.

---

## Demo Console (no terminal)

For demos — or if you'd rather not touch the terminal — launch the **Demo Console**:

```bash
./scripts/demo-console.sh        # opens http://localhost:9700 in your browser
```

A small local web UI (a dependency-free Node helper) that drives everything from
the browser:

- **▶ Start everything** — the 5 services **and** the UI shell, then seeds (first run also installs UI deps; ≈1–2 min)
- **■ Stop everything** — frees all ports, including the UI (`:4200`)
- **↻ Seed / reset data**
- **Statement Analysis v1 ⇄ v2** — flip the micro-app version live, then refresh the phone
- **Live service health** — green/red dots per service

The only terminal command is launching the console; everything else is buttons.
(Under the hood it shells out to `run-local.sh` / `stop-local.sh`.) All actions
run server-side in the helper, so there are no CORS issues.

---

## The pieces

| # | Service | Port | Role | Depends on |
|---|---------|------|------|------------|
| 1 | **statement-service** | 8082 | Mock statements & transactions | — |
| 2 | **product-service** | 8085 | Product catalog | — |
| 3 | **platform-service** | 8079 | Micro-frontend manifest + JS bundles | — |
| 4 | **analysis-service** | 8083 | Spending analysis (heuristic) | statement-service |
| 5 | **recommendation-service** | 8084 | Rule-based recommendations | analysis-service |
| 6 | **advisor-service** | 8086 | AI wealth advisor — conversational agent (OpenAI) | statement + analysis + product |
| 7 | **onboarding-service** | 8087 | Resumable CASA + Takaful onboarding workflows | — (standalone) |
| — | **banking-shell** (UI) | 4200 | Ionic/Angular host app | platform-service (+ the above) |

**Why this order:** the chain `statement → analysis → recommendation` is
order-sensitive (each calls the previous). `product` and `platform` are
standalone. Start them top-to-bottom; the UI last.

---

## Step 1 — Start the services (each in its own terminal tab)

Run these in order. Leave each one running. A service is ready when its log shows
`Akka Runtime started at 127.0.0.1:<port>`.

```bash
# 1. statement-service  (:8082)
mvn -f backend/statement-service/pom.xml clean compile exec:java

# 2. product-service  (:8085)
mvn -f backend/product-service/pom.xml clean compile exec:java

# 3. platform-service  (:8079)
mvn -f platform/platform-service/pom.xml clean compile exec:java

# 4. analysis-service  (:8083)   — needs #1 running + seeded
mvn -f backend/analysis-service/pom.xml clean compile exec:java

# 5. recommendation-service  (:8084)   — needs #4 running
mvn -f backend/recommendation-service/pom.xml clean compile exec:java

# 6. advisor-service  (:8086)   — AI wealth advisor; needs #1, #4, and product running.
#    Set OPENAI_API_KEY in this shell FIRST for real answers (without it, the chat
#    replies with a graceful "talk to a human advisor" fallback rather than crashing).
export OPENAI_API_KEY=sk-...
mvn -f backend/advisor-service/pom.xml clean compile exec:java

# 7. onboarding-service  (:8087)   — resumable CASA + Takaful onboarding (no API key needed)
mvn -f backend/onboarding-service/pom.xml clean compile exec:java
```

The advisor is also the **Advisor** tab in the UI (a chat micro-app, `mf-advisor`).
Try: *"Can I afford to save for a holiday in 2 years?"* — it grounds the answer in
the account's real credits/debits, projects affordability, proposes a tabung goal,
and offers a human handoff.

The **Onboard** tab (micro-app `mf-onboarding`) runs the resumable CASA / Takaful
journeys: start an application, fill step 1, **close/refresh the app**, reopen → it
resumes at step 2 (stored via `localStorage`), then completes with an account or a
takaful policy. Do NOT restart onboarding-service mid-flow when demoing resume — the
local dev journal is in-memory, so "app close" means the client stops, not the service.

> Tip: `clean` forces a full rebuild. After the first run you can drop it
> (`mvn -f ... compile exec:java`) for faster restarts.

## Step 2 — Seed data

Three services hold data and expose a seed endpoint. Seeding emits the events
that create the entities (so the views and downstream services have something to
read).

```bash
curl -X POST http://localhost:8082/accounts/seed         # statements
curl -X POST http://localhost:8085/products/seed         # products
curl -X POST http://localhost:8079/microfrontends/seed   # micro-frontend manifest
```

## Step 3 — Verify (optional smoke test)

```bash
curl http://localhost:8082/accounts/acc-1001/statements
curl http://localhost:8085/products
curl http://localhost:8079/microfrontends/manifest/demo
curl "http://localhost:8083/accounts/acc-1001/analysis/summary?statementId=stmt-2025-12"
curl "http://localhost:8084/accounts/acc-1001/recommendations?statementId=stmt-2025-12"
```

Each should return JSON (add `| python3 -m json.tool` to pretty-print). Or just
run `./scripts/smoke-local.sh`, which waits, seeds, and checks all five with
PASS/FAIL.

## Step 4 — Run the UI

```bash
cd mobile/banking-shell
npm install        # first time only
npm start          # ng serve on http://localhost:4200
```

Open **http://localhost:4200**. On a desktop browser the app renders inside an
**iPhone Pro Max device frame**; on a narrow window / real phone it fills the
screen. The Statements, Analysis, and Recommendations tabs load their
micro-apps from `platform-service` and pull data from the backend services.

---

## Switching a micro-app version live (v1 → v2)

The versioned app is the **`statement-analysis` micro-app** — the one rendered in
the app's **Analysis tab**.

> It is **not** the `analysis-service` backend. `analysis-service` just *computes*
> the spending numbers; the thing that has **versions** is the front-end **bundle**
> that displays them.

Two versions are published in `platform-service`:
- **`1.0.0`** — basic category table
- **`2.0.0`** — adds a **Top Merchants** section + a **"V2"** badge

You switch between them by changing the **manifest** — no rebuild, no service
restart, no app-store release. The shell loads the new version on the next page
load. This is the "config-driven UI / change at the speed of business" demo.

**Check the current version:**
```bash
curl http://localhost:8079/microfrontends/manifest/demo
```

**Switch to v2** (PUT the manifest with `statement-analysis` at `2.0.0`):
```bash
curl -X PUT http://localhost:8079/microfrontends/manifest/demo \
  -H 'Content-Type: application/json' -d '{
  "manifestVersion": "2026-02-20T00:00:00Z",
  "channel": "demo",
  "microfrontends": [
    {"name":"statement-details","version":"1.0.0","remoteEntry":"/bundles/statement-details/1.0.0/main.js","exposedModule":"./Module","elementTag":"mf-statement-details"},
    {"name":"statement-analysis","version":"2.0.0","remoteEntry":"/bundles/statement-analysis/2.0.0/main.js","exposedModule":"./Module","elementTag":"mf-statement-analysis"},
    {"name":"recommendations","version":"1.0.0","remoteEntry":"/bundles/recommendations/1.0.0/main.js","exposedModule":"./Module","elementTag":"mf-recommendations"}
  ]
}'
```

**Switch back to v1:** run the same command but set `statement-analysis`
`"version":"1.0.0"` and `"remoteEntry":"/bundles/statement-analysis/1.0.0/main.js"`.

Then **hard-refresh the browser (⌘⇧R)**. A full reload is required — a custom
element (`mf-statement-analysis`) can't be *redefined* in a page that already
loaded the previous version, so just navigating to the tab won't switch it.

---

## Stop everything

```bash
./scripts/stop-local.sh        # kills the services and frees the ports
```
(Or `Ctrl-C` in each service tab; `Ctrl-C` the UI tab.)

---

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `400 No statement found for id 'stmt-2025-12'` (from analysis) | statement-service is running but **not seeded** → run the Step 2 seeds. |
| analysis / recommendation errors with a `http://statement-service/...` or `http://analysis-service/...` message | A dependency isn't running. Start in order (Step 1) and seed. |
| Endpoint returns empty right after seeding | Views are **eventually consistent** — wait a second and retry. |
| `port already in use` | Something's still running → `./scripts/stop-local.sh`, or `lsof -ti :<port> -sTCP:LISTEN`. |
| UI tabs show "Unable to Load" | platform-service must be running + seeded (it serves the manifest and bundles). |
| Recommendations tab empty | needs analysis-service (which needs statement-service) up + seeded. |

## Shortcuts recap

| Script | Does |
|---|---|
| `./scripts/run-local.sh [--clean]` | start all 5 (right order) → wait → seed → smoke-verify |
| `./scripts/smoke-local.sh` | wait → seed (idempotent) → curl every endpoint (PASS/FAIL) |
| `./scripts/stop-local.sh` | stop services, free ports |
| `scripts/warp/mbsb-local-services.yaml` | Warp launch config — live pane per service + seed/smoke tab |
