# Banking Micro-Frontends Demo Prototype

A standalone HTML/JS prototype demonstrating the micro-frontends architecture: a mobile banking shell that dynamically loads micro-apps based on a manifest configuration.

## Run Locally

```bash
# Option 1: Python
python3 -m http.server 3000 -d demo-prototype

# Option 2: npx
npx serve demo-prototype -l 3000
```

Then open **http://localhost:3000**

## What You'll See

- **Left side**: A mobile phone frame showing the banking app with tab navigation
- **Right side**: A configuration panel showing the current manifest and quick-action buttons

### Tabs

| Tab | Micro-App | Description |
|-----|-----------|-------------|
| Home | (built-in) | Balance card, quick actions, recent transactions |
| Statements | `mf-statement-details` | Expandable statement cards with transaction tables |
| Analysis | `mf-statement-analysis` | Spending breakdown, category table, insights (v1 & v2) |
| Tips | `mf-recommendations` | Product recommendation cards |

### Demo Scenarios

1. **Remove a micro-app**: Click "Remove Recommendations" — the Tips tab disappears instantly
2. **Add it back**: Click "Add Recommendations" — the Tips tab reappears
3. **Version swap**: Click "Switch Analysis to v2" — the Analysis tab re-renders with a green theme and Top Merchants section
4. **Remove all**: Click "Remove All Micro-Apps" — only the Home tab remains
5. **Restore**: Click "Restore Default" — all 3 micro-apps return

### Architecture Highlights

- Each micro-app is a real **Web Component** (`customElements.define`) with Shadow DOM encapsulation
- The shell dynamically injects `<script>` tags at runtime based on the manifest (same pattern as production)
- Manifest changes trigger tab bar re-rendering and micro-app re-loading
- No build tools required — pure HTML, CSS, and vanilla JavaScript
