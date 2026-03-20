# Micro-Frontends Architecture

## 1. What Are Micro-Frontends?

The same idea as microservices, but for the UI. Instead of one monolithic frontend app, you split the UI into small, independently built and deployed pieces. Each piece is a **micro-app** — a self-contained Angular application compiled into a single JavaScript file (a **bundle**).

The key benefit: you can update one micro-app (say, swap statement-analysis from v1 to v2) without touching, rebuilding, or redeploying anything else.

---

## 2. The Three Layers

This project has three distinct layers:

| Layer | What it does | Components |
|-------|-------------|------------|
| **Backend** | Business logic + data | 4 Akka SDK services (statement, product, analysis, recommendation) |
| **Platform** | Discovers and delivers micro-apps | Manifest registry + bundle serving |
| **Mobile** | Hosts and renders micro-apps | Banking shell + 3 micro-apps |

---

## 3. The Shell — The Host App

The shell is at `mobile/banking-shell/`. It's an **Ionic 8 + Angular 19** app that acts as the container. It provides the tab navigation, layout, and the machinery to load micro-apps at runtime.

**The shell does NOT contain any micro-app code.** It only knows micro-app *names* — like `"statement-details"`.

### How a Tab Page Works

Each tab page is trivially simple. For example, `statements.component.ts`:

```typescript
template: `
  <ion-header>...<ion-title>Statements</ion-title>...</ion-header>
  <ion-content>
    <app-microapp-loader microappName="statement-details"></app-microapp-loader>
  </ion-content>
`
```

It just says "load the micro-app named `statement-details` here." The heavy lifting happens in two files:

### MicroAppService (`services/microapp.service.ts`) — The Runtime Engine

This service handles the entire micro-app lifecycle:

1. **Fetch the manifest** — HTTP GET to the platform service asking "what micro-apps exist and where are their bundles?"
2. **Find the entry** — look up the requested micro-app by name in the manifest
3. **Inject the script** — create a `<script>` tag pointing to the bundle URL and append it to `<head>`
4. **Wait for registration** — the script, when it executes, registers a Web Component (custom HTML element). The service uses `customElements.whenDefined()` to know when it's ready

### MicroAppLoaderComponent (`microapp-loader/microapp-loader.component.ts`) — The Renderer

This component orchestrates the loading and handles UI states (spinner, error with retry, loaded). Once the Web Component is registered, it does:

```typescript
const element = document.createElement('mf-statement-details');  // the custom element tag
element.setAttribute('account-id', 'acc-1001');                  // pass data via attributes
hostDiv.appendChild(element);                                     // render it
```

That's it. The micro-app takes over from there.

---

## 4. What Is a Bundle?

A **bundle** is the compiled output of a micro-app — a single JavaScript file (`main.js`) that contains everything that micro-app needs to run.

When you build a micro-app with `ng build --configuration production`, Angular compiles all TypeScript, templates, and styles into a minified JS file. We use `"outputHashing": "none"` in the angular.json so the filename is always `main.js` (not `main.abc123.js`), which makes it predictable for the manifest.

The bundles in this project:

| Bundle | Size | What it does |
|--------|------|-------------|
| `statement-details/1.0.0/main.js` | 166 KB | Shows account statements with expandable transaction details |
| `statement-analysis/1.0.0/main.js` | 171 KB | Spending analysis with charts (v1 — basic) |
| `statement-analysis/2.0.0/main.js` | 171 KB | Spending analysis with charts (v2 — adds top merchants, teal theme) |
| `recommendations/1.0.0/main.js` | 148 KB | Product recommendations based on spending patterns |

---

## 5. How Micro-Apps Become Web Components

Each micro-app's `main.ts` does this:

```typescript
(async () => {
  // 1. Create a headless Angular application (no DOM bootstrap)
  const appRef = await createApplication(appConfig);

  // 2. Wrap the root component as a Web Component (custom element)
  const element = createCustomElement(AppComponent, { injector: appRef.injector });

  // 3. Register it with the browser's custom element registry
  customElements.define('mf-statement-details', element);
})();
```

This uses `@angular/elements` — Angular's bridge to the Web Components standard. After this code runs, `<mf-statement-details>` becomes a valid HTML tag that any page can use, regardless of framework.

The micro-app's `AppComponent` is a regular Angular component that:
- Reads the `account-id` attribute (passed by the shell)
- Makes HTTP calls directly to the backend services
- Renders its own UI with its own styles

---

## 6. The Platform Layer — In Detail

The platform has one job: **tell the shell where to find each micro-app's bundle**. It does this through two services that have been unified into a single Akka SDK service.

### Generation 1 (Legacy) — Two Standalone Servers

These are plain Java HTTP servers using `com.sun.net.httpserver` — no framework, no Akka:

**Registry** (`platform/microfrontend-registry/`) — port 8079
- Serves `GET /microfrontends/manifest`
- Reads `manifest.json` from disk on every request
- To change versions, you edit the JSON file directly

**CDN** (`platform/microfrontend-cdn/`) — port 8090
- Serves any file under a `www/` directory
- Static file server with MIME type detection and path traversal protection

### Generation 2 (Current) — Unified Akka SDK Service

The new `platform/platform-service/` combines both into a single Akka SDK service on port 8079. Five source files:

#### Domain Layer

**`Manifest.java`** — The data model:

```
Manifest
├── manifestVersion: String     (timestamp of last update)
├── channel: String             (e.g., "demo" — supports multiple audiences)
└── microfrontends: List<ManifestEntry>
    ├── name: String            (identifier, e.g., "statement-details")
    ├── version: String         (semver, e.g., "1.0.0")
    ├── remoteEntry: String     (URL to the JS bundle)
    ├── exposedModule: String   (always "./Module" — convention)
    └── elementTag: String      (Web Component tag, e.g., "mf-statement-details")
```

**`ManifestEvent.java`** — Events for the event-sourced entity:
- `ManifestCreated` — emitted when a channel's manifest is first seeded
- `ManifestUpdated` — emitted on version swaps

#### Application Layer

**`ManifestEntity.java`** — An `EventSourcedEntity` where the entity ID is the channel name (e.g., `"demo"`):

| Command | What it does |
|---------|-------------|
| `getManifest()` | Returns current manifest state |
| `create(manifest)` | Idempotent seed — emits `ManifestCreated` if entity doesn't exist |
| `update(manifest)` | Version swap — emits `ManifestUpdated`, errors if not seeded |

The `applyEvent` method reconstitutes state from events:

```java
return switch (event) {
    case ManifestCreated created -> created.manifest();
    case ManifestUpdated updated -> updated.manifest();
};
```

#### API Layer

**`ManifestEndpoint.java`** — `@HttpEndpoint("/microfrontends")`:

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/microfrontends/manifest/{channel}` | Shell calls this to discover micro-apps |
| `PUT` | `/microfrontends/manifest/{channel}` | Update manifest for version swap demo |
| `POST` | `/microfrontends/seed` | Seeds the "demo" channel with all 3 micro-apps |

The seed method creates entries with **relative** bundle URLs like `/bundles/statement-details/1.0.0/main.js` (no hardcoded hostname), so they work in any environment.

**`BundleEndpoint.java`** — `@HttpEndpoint("/bundles")`:

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/bundles/{name}/{version}/{filename}` | Serves JS bundles from classpath |

It reads from `www/{name}/{version}/{filename}` inside the JAR. Bundles are baked into the artifact at build time — no separate file server needed. Includes directory traversal protection and proper content-type headers.

---

## 7. How to Build Everything

### Step 1: Build the Micro-Apps

Each micro-app is an Angular project. Install deps and build:

```bash
# Statement Details (v1 only)
cd mobile/microapps/statement-details
npm install
npx ng build --configuration production

# Statement Analysis (v1 + v2)
cd mobile/microapps/statement-analysis
npm install
npx ng build --configuration production     # v1
npx ng build --configuration v2             # v2

# Recommendations (v1 only)
cd mobile/microapps/recommendations
npm install
npx ng build --configuration production
```

Output lands in `dist/browser/main.js` for each.

Or use the script: `bash scripts/build-microapps.sh`

### Step 2: Copy Bundles into Platform Service Resources

```bash
# Create target directories
mkdir -p platform/platform-service/src/main/resources/www/statement-details/1.0.0
mkdir -p platform/platform-service/src/main/resources/www/statement-analysis/1.0.0
mkdir -p platform/platform-service/src/main/resources/www/statement-analysis/2.0.0
mkdir -p platform/platform-service/src/main/resources/www/recommendations/1.0.0

# Copy bundles (adjust source paths based on actual build output)
cp mobile/microapps/statement-details/dist/browser/main.js \
   platform/platform-service/src/main/resources/www/statement-details/1.0.0/

cp mobile/microapps/statement-analysis/dist/browser/main.js \
   platform/platform-service/src/main/resources/www/statement-analysis/1.0.0/  # after v1 build

cp mobile/microapps/statement-analysis/dist/browser/main.js \
   platform/platform-service/src/main/resources/www/statement-analysis/2.0.0/  # after v2 build

cp mobile/microapps/recommendations/dist/browser/main.js \
   platform/platform-service/src/main/resources/www/recommendations/1.0.0/
```

### Step 3: Build and Run the Platform Service

```bash
cd platform/platform-service
mvn compile exec:java
```

This starts the Akka SDK service on port 8079. On first run, seed the manifest:

```bash
curl -X POST http://localhost:8079/microfrontends/seed
```

Then verify:

```bash
# Get the manifest
curl http://localhost:8079/microfrontends/manifest/demo

# Fetch a bundle
curl http://localhost:8079/bundles/statement-details/1.0.0/main.js | head -c 200
```

### Step 4: Build and Run the Shell

```bash
cd mobile/banking-shell
npm install
npx ng serve
```

The shell opens on `http://localhost:4200`, fetches the manifest from `localhost:8079`, and loads micro-apps dynamically.

---

## 8. The Complete Runtime Flow

```
User taps "Statements" tab
        |
        v
Shell router lazy-loads StatementsComponent
        |
        v
StatementsComponent renders <app-microapp-loader microappName="statement-details">
        |
        v
MicroAppLoaderComponent.ngOnInit()
        |
        v
MicroAppService.getManifestEntry("statement-details")
        |
        v
HTTP GET http://localhost:8079/microfrontends/manifest/demo
        |
        v
ManifestEndpoint -> ManifestEntity -> returns Manifest state
        |
        v
Find entry where name == "statement-details"
  -> remoteEntry: "/bundles/statement-details/1.0.0/main.js"
        |
        v
MicroAppService.injectScript()
  -> creates <script src="/bundles/statement-details/1.0.0/main.js">
        |
        v
BundleEndpoint serves www/statement-details/1.0.0/main.js from classpath
        |
        v
Browser executes the script:
  1. createApplication(appConfig)          — headless Angular bootstrap
  2. createCustomElement(AppComponent)     — wrap as Web Component
  3. customElements.define('mf-statement-details', ...)  — register tag
        |
        v
MicroAppService.waitForElement('mf-statement-details')
  -> customElements.whenDefined() resolves
        |
        v
MicroAppLoaderComponent.renderElement('mf-statement-details')
  -> document.createElement('mf-statement-details')
  -> element.setAttribute('account-id', 'acc-1001')
  -> hostDiv.appendChild(element)
        |
        v
Micro-app renders, calls backend APIs directly:
  GET http://localhost:8082/accounts/acc-1001/statements
```

---

## 9. The Version Swap Demo

This is the killer feature of micro-frontends. To swap `statement-analysis` from v1 to v2 **without restarting anything**:

```bash
curl -X PUT http://localhost:8079/microfrontends/manifest/demo \
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

Next time the user navigates to the Analysis tab, the shell fetches the updated manifest, sees `2.0.0`, loads the new bundle, and v2 renders — with teal theme, top merchants section, and a "v2" badge. No redeployment of any service. No rebuild of the shell.

The v1/v2 difference is controlled by Angular's file replacement mechanism in `angular.json`:
- `production` config uses `environment.ts` where `version = 1`
- `v2` config swaps in `environment.v2.ts` where `version = 2`
- The component reads `environment.version` and renders differently based on it

---

## 10. The Mental Model

Think of it like a TV with channels:

| Concept | Analogy |
|---------|---------|
| **Shell** | The TV set — provides the screen, remote (tabs), and power |
| **Manifest** | The channel guide — lists what's on and where to tune |
| **Bundles** | The shows — independent content, produced separately |
| **Platform service** | The cable box — delivers the guide and streams the content |
| **Backend services** | The production studios — create the data the shows display |
| **Version swap** | Changing the channel lineup — swap a show without buying a new TV |

---

## 11. Key Design Decisions

### Why Web Components + Script Injection (not Module Federation)?

This project uses the **Web Components + dynamic script injection** pattern rather than Webpack Module Federation. The trade-offs:

| | Web Components + Script Injection | Module Federation |
|-|----------------------------------|-------------------|
| **Framework coupling** | None — any framework can produce/consume | Webpack-specific (or rspack) |
| **Build tooling** | Standard `ng build` | Requires Module Federation plugin |
| **Shared dependencies** | Each bundle is self-contained | Can share Angular, RxJS across remotes |
| **Bundle size** | Larger (each includes Angular runtime) | Smaller (shared runtime) |
| **Simplicity** | Very simple — just a `<script>` tag | More complex configuration |
| **Isolation** | Complete — each micro-app is sandboxed | Partial — shared scope can conflict |

For a demo/SPOV project, the simplicity of Web Components is the right choice.

### Why Event Sourcing for the Manifest?

The manifest could be stored in a simple key-value store, but event sourcing gives:
- **Audit trail** — every version swap is recorded as an event
- **Replay** — can reconstruct any historical manifest state
- **Consistency** — Akka's entity model guarantees no concurrent conflicts per channel
- **Platform deployment** — runs natively on Akka Platform with no external database

### Why Classpath Resources for Bundles?

Baking bundles into the JAR means:
- **Single deployable** — one artifact contains everything
- **No external storage** — no S3, no CDN, no volume mounts
- **Versioning** — the JAR itself is the release artifact
- **Trade-off** — updating a bundle requires rebuilding and redeploying the JAR
