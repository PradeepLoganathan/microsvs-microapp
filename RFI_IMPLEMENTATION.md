# RFI Implementation Plan — MBSB Project NextGen demo

> **Purpose:** drive this repo to a live demo for the **MBSB Bank "Project NextGen" RFI** (replace the legacy MJourney mobile/web banking app). This file is **self-contained** — a Claude Code session in this folder can build from it without any other repo.
>
> **Thesis the demo must prove:** a real, running banking app composed from a handful of Akka building blocks — **build fast, own the IP, AI-native, resilient.** Every visible customer moment maps to an Akka primitive.
>
> **Workflow:** follow `CLAUDE.md` — build **one component + its test at a time**, design-first, wait for approval. Read the relevant `akka-context/sdk/*.html.md` before a new component type (esp. **Agents** and **Workflows**).
>
> **Demo discipline:** runs **fully local / offline** (no venue wifi). **eKYC is stubbed.** Keep a reset-to-fresh-visitor script + a happy-path screen recording as fallback.

---

## "Sara's Day" — the demo arc (what we're building toward)

Sara (32, KL professional, curious about Islamic banking) goes from stranger → engaged customer:

1. **Visitor** — opens the app, no account; a rich pre-login home already engages her.
2. **Register** — email/phone (no eKYC); gets an instantly personalized welcome offer.
3. **Onboard** — opens a CASA account; abandons mid-flow, reopens → **resumes exactly where she left off**; then adds **Takaful** via a different journey.
4. **In-app life** — balances, a **tabung** savings goal, spending insight; mid-session a **next-best-action** offer surfaces at the moment of need.
5. *(engagement loop — reward + survey + NPS — secondary)*
6. **AI wealth advisor ⭐** — asks "can I afford to save for Hajj in 2 years?"; the agent uses her real data, reasons, proposes a goal/takaful, and offers a human handoff.
7. *(operator console — live widget config, campaign dashboard, A/B — secondary)*

---

## What is KEY for the RFI (must-build) vs stretch

**MUST-BUILD (the differentiated core — implement these completely):**
- **K1 — Conversational AI wealth advisor** (beat 6, RFI §4.5.3) — *the peak; never cut.*
- **K2 — Resumable CASA + Takaful onboarding** (beat 3, §4.1.1/4.1.2) — the "build-fast/composable" proof.
- **K3 — Customer lifecycle Visitor→Registered→Customer + pre-login + welcome offer** (beats 1–2, §4.2.1/4.2.2/4.3.1).
- **K4 — In-app home: tabung goal + spending + NBA at moment of need** (beat 4, §4.2.3/4.4.2).
- **K5 — Showcase what already exists:** micro-frontend config-driven UI (no app-store release, §4.4.3) + Akka microservices (§4.6.3) + AI spending analysis. *(No build — narration + a small polish.)*

**STRETCH (build only if K1–K4 are solid):**
- **S1 — Engagement loop:** gamification + per-feature survey + NPS (beat 5, §4.3.2/4.3.5/4.3.7).
- **S2 — Operator console:** live widget config (reuse manifest), campaign dashboard, A/B (beat 7, §4.4.3/4.3.4/4.3.8).

**Definition of done (RFI demo):** Sara's journey runs end-to-end locally — pre-login → register → CASA onboarding *with resume* → in-app home with a tabung goal + an in-session NBA → a real conversational advisor exchange grounded in her data with a human-handoff — plus a live micro-frontend swap to show config-driven UI. Reset script + recording exist.

---

## Reuse — existing components & patterns (don't rebuild)

Akka SDK (Java 21), packages `domain` / `application` / `api`. Ports: statement 8082 · analysis 8083 · recommendation 8084 · product 8085 · registry 8079 · cdn 8090 · shell 4200.

| Service | Components | Reuse for |
|---|---|---|
| statement-service | `StatementEntity` (EventSourced), `StatementsByAccountView`, `StatementEndpoint`, `MockDataProvider` | account/txn data for advisor + spending |
| analysis-service | **`TransactionAnalysisAgent extends Agent`** (`@FunctionTool` fetch/categorize/persist), `AnalysisEndpoint` | **clone this Agent pattern for the advisor (K1)**; reuse spending profile |
| product-service | `ProductEntity` (EventSourced), `AllProductsView`, `ProductEndpoint` | product catalog (CASA/takaful/wealth) for advisor + onboarding |
| recommendation-service | `RecommendationEngine` (rule-based), `RecommendationEndpoint` | enhance for NBA (K4) |
| platform-service | **`ManifestEntity` (EventSourced, per-channel)**, `ManifestEndpoint`, `BundleEndpoint` | config-driven UI / no-release swap (K5); operator widget config (S2) |
| mobile/banking-shell | Ionic/Angular shell, manifest-driven micro-apps | host new micro-frontends |
| mobile/microapps | recommendations, statement-analysis, statement-details | add: pre-login, register, onboarding, home, advisor |

---

## Build backlog (prioritized — design each, then build per CLAUDE.md)

### K1 — Wealth Advisor Agent ⭐ (highest value)
- **Read first:** `akka-context/sdk/agents.html.md`, `agents/memory.html.md`, `agents/extending.html.md`, `agents/guardrails.html.md`.
- **Where:** analysis-service (or a new `advisor-service`).
- **Component:** `WealthAdvisorAgent extends Agent`, `@Component(id="wealth-advisor")`. One command handler `ask(Question)` (agents allow ONE handler) → multi-turn via **session memory** (session id per customer conversation). System message: Shariah-aware advisor; ground answers in real data; propose exactly one action; defer consequential actions to a human.
- **`@FunctionTool` banking tools (the agent calls these):** `getAccountSummary(customerId)`, `getSpendingProfile(customerId)` (→ analysis-service), `getGoals(customerId)` (→ K4), `listProducts(type)` (→ product-service), `projectAffordability(customerId, target, months)`, `proposeAction(customerId, action)` (returns a structured proposal card — use structured output), `requestHumanHandoff(customerId, topic)` (HITL — logs request, replies "an advisor will follow up").
- **Guardrail:** propose, never execute money-movement/product-open without explicit confirm + handoff.
- **Endpoint + micro-frontend:** `AdvisorEndpoint` (POST /advisor/{customerId}/ask, streaming optional); an `advisor` chat micro-app in the shell.
- **Acceptance:** "Can I afford to save for Hajj in 2 years?" → calls getAccountSummary + getSpendingProfile + projectAffordability → grounded answer with real numbers → proposes a tabung goal → offers human handoff. No canned reply.

### K2 — Resumable onboarding workflows
- **Read first:** `akka-context/sdk/workflows.html.md` (saga, recovery, durable resume) — **no Workflow exists in this repo yet.**
- **Where:** new `onboarding-service`.
- **Components:** `CasaOnboardingWorkflow extends Workflow<CasaState>` — steps: collectDetails → eKYC(stub) → createAccount → welcome (state persists between steps → **survives app close**). `TakafulOnboardingWorkflow` — a *distinct* set of steps (prove it's not a copy). `OnboardingEndpoint` — start / submitStep / getStatus / resume.
- **Acceptance:** start CASA → submit step 1 → (simulate app close) → `getStatus` = in-progress at step 2 → reopen resumes → complete. Then start Takaful with different steps. **The resume must be bulletproof — it's the money shot.**

### K3 — Customer lifecycle + pre-login + welcome offer
- **Read first:** `akka-context/sdk/event-sourced-entities.html.md`, `views.html.md`.
- **Where:** new `customer-service`.
- **Components:** `CustomerEntity extends EventSourcedEntity<Customer, CustomerEvent>` — stage VISITOR→REGISTERED→CUSTOMER (events `VisitorCreated`, `Registered`, `KycCompleted`) — **same entity evolves, no migration.** `PreLoginView` (projection for pre-login content). `CustomerEndpoint` — createVisitor / register / completeKyc / get. On register → emit a behaviour-driven welcome offer (links to K4/recommendation).
- **Micro-frontends:** pre-login home + register.
- **Acceptance:** cold open mints a visitor id + pre-login tiles; register → REGISTERED + personalized welcome; stubbed eKYC → CUSTOMER. Same id throughout.

### K4 — In-app home: tabung goal + NBA at moment of need
- **Where:** `GoalEntity extends EventSourcedEntity` (in customer-service or a new `goals-service`); enhance recommendation-service.
- **Components:** `GoalEntity` (create/contribute/getProgress); home micro-frontend (balances + tabung progress + spending insight, reusing analysis-service). **NBA:** add an **in-session trigger** to recommendation-service — when a triggering event occurs (e.g. viewing a large txn), surface the matched offer *then*, relevant/dismissable/explained (not on a dashboard).
- **Acceptance:** create + contribute to a tabung goal; in-session, after viewing a large txn, a relevant explained offer appears at the moment of need.

### K5 — Showcase existing (polish, not build)
- Ensure the **manifest swap** demo works crisply: change `ManifestEntity` for channel `demo` → the shell reflects a new/updated widget **without a redeploy or app-store release** (RFI §4.4.3, the "own it / change at the speed of business" proof).
- Confirm spending analysis (analysis-service agent) + recommendations render.

### Stretch — S1 engagement loop, S2 operator console (only after K1–K4)
- S1: event-sourced points/rewards `Entity` + survey/NPS capture endpoints + results `View`.
- S2: reuse `ManifestEntity` for live widget config; campaign-events `View`; A/B assignment + results `View`.

---

## Recommended build order
**K1 → K2 → K3 → K4 → K5 (verify) → (stretch S1, S2).**
Rationale: the advisor (K1) and the resume-workflow (K2) prove the differentiated thesis hardest; K3/K4 complete Sara's core journey; K5 is already built (just verify); stretch only if time remains.

## Cross-cutting
- New services follow the existing structure (`domain`/`application`/`api`, pom, `run-all.sh` wiring, a port).
- New micro-frontends follow `mobile/microapps/*` + `build-microapps.sh` / `publish-microapps.sh` + manifest registration.
- Add an integration test per endpoint (per CLAUDE.md).
- Update `scripts/run-all.sh` and the README port table when adding a service.
- Add/extend the **reset-to-fresh-visitor** script and re-record the happy path after each major beat lands.

## RFI requirements covered live vs verbally
- **Live (this plan):** §4.1.1/4.1.2 (onboarding CASA+takaful), §4.2.1/4.2.2/4.2.3, §4.3.1 (+ §4.3.2/4.3.5/4.3.7 if S1), §4.4.1/4.4.2/4.4.3, §4.5.3, §4.6.3.
- **Verbal / written response (not in demo):** §4.3.3 omnichannel/WhatsApp, §4.3.6 sentiment, §4.5.1 partner integration, §4.5.2 embedded banking, §4.6.1 dispute mgmt, §4.6.4 autoscaling (resilience widget covers this).
