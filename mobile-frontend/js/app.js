// ============================================
// Banking Shell — App Engine
// Mirrors MicroAppService + MicroAppLoaderComponent
// ============================================

(function () {
  'use strict';

  // --- State ---
  let currentManifest = JSON.parse(JSON.stringify(window.MOCK_DATA.defaultManifest));
  let activeTab = 'home';
  const scriptStates = new Map(); // name → { loaded, loading, error }
  let deployTimer = null; // pending deploy timeout

  // --- Deploy delay (ms) — simulates real deployment propagation ---
  // Random delay between 30–45 seconds per deployment
  function getDeployDelay() {
    return 30000 + Math.random() * 15000;
  }

  // --- BroadcastChannel for cross-page sync ---
  const channel = new BroadcastChannel('manifest-sync');

  channel.onmessage = (event) => {
    const { type, manifest } = event.data;
    if (type === 'manifest-update' && manifest) {
      scheduleDeployment(manifest);
    } else if (type === 'request-state') {
      // Config panel is asking for current state — send it back
      channel.postMessage({
        type: 'state-sync',
        manifest: JSON.parse(JSON.stringify(currentManifest)),
        activeTab,
      });
    }
  };

  // --- Delayed deployment with spinner overlay ---

  function scheduleDeployment(manifest) {
    // If a deploy is already pending, cancel it and restart with the new manifest
    if (deployTimer) {
      clearTimeout(deployTimer);
    }

    showDeployOverlay();

    deployTimer = setTimeout(() => {
      deployTimer = null;
      hideDeployOverlay();
      applyManifest(manifest);
    }, getDeployDelay());
  }

  function showDeployOverlay() {
    let overlay = document.getElementById('deploy-overlay');
    if (overlay) return; // already showing

    overlay = document.createElement('div');
    overlay.id = 'deploy-overlay';
    overlay.innerHTML = `
      <div class="deploy-spinner"></div>
      <p class="deploy-text">Deploying changes...</p>
      <p class="deploy-subtext">Propagating to edge nodes</p>
    `;

    // Insert inside the phone screen, on top of content
    const phoneScreen = document.querySelector('.phone-screen');
    if (phoneScreen) {
      phoneScreen.appendChild(overlay);
    }
  }

  function hideDeployOverlay() {
    const overlay = document.getElementById('deploy-overlay');
    if (overlay) {
      overlay.classList.add('deploy-fade-out');
      setTimeout(() => overlay.remove(), 300);
    }
  }

  // --- Tab definitions (home is always present; others depend on manifest) ---
  const TAB_DEFS = {
    home:             { label: 'Home',       icon: 'home-outline',      microapp: null },
    statements:       { label: 'Statements', icon: 'receipt-outline',   microapp: 'statement-details' },
    analysis:         { label: 'Analysis',   icon: 'analytics-outline', microapp: 'statement-analysis' },
    recommendations:  { label: 'Tips',       icon: 'bulb-outline',      microapp: 'recommendations' },
  };

  // --- DOM refs ---
  const contentEl = document.getElementById('app-content');
  const tabBarEl = document.getElementById('tab-bar');

  // --- Public API (kept for backward compatibility) ---
  window.ShellApp = {
    applyManifest,
    getManifest: () => JSON.parse(JSON.stringify(currentManifest)),
    getActiveTab: () => activeTab,
  };

  // --- Initialize ---
  renderTabBar();
  switchTab('home');

  // Announce presence so config panel knows phone is connected
  channel.postMessage({
    type: 'state-sync',
    manifest: JSON.parse(JSON.stringify(currentManifest)),
    activeTab,
  });

  // ========== Manifest ==========

  function applyManifest(newManifest) {
    currentManifest = JSON.parse(JSON.stringify(newManifest));

    // If the active tab's micro-app was removed, fall back to home
    if (activeTab !== 'home') {
      const def = TAB_DEFS[activeTab];
      if (def && def.microapp && !findManifestEntry(def.microapp)) {
        activeTab = 'home';
      }
    }

    renderTabBar();
    switchTab(activeTab);
  }

  function findManifestEntry(name) {
    return currentManifest.microfrontends.find(mf => mf.name === name);
  }

  // ========== Tab Bar ==========

  function renderTabBar() {
    tabBarEl.innerHTML = '';

    Object.entries(TAB_DEFS).forEach(([tabId, def]) => {
      // Skip tabs whose micro-app isn't in the manifest
      if (def.microapp && !findManifestEntry(def.microapp)) return;

      const btn = document.createElement('button');
      btn.className = 'tab-button' + (tabId === activeTab ? ' active' : '');
      btn.innerHTML = `<ion-icon name="${def.icon}"></ion-icon><span>${def.label}</span>`;
      btn.addEventListener('click', () => switchTab(tabId));
      tabBarEl.appendChild(btn);
    });
  }

  // ========== Tab Switching ==========

  function switchTab(tabId) {
    activeTab = tabId;

    // Update active class on tab buttons
    tabBarEl.querySelectorAll('.tab-button').forEach((btn, i) => {
      const tabs = getVisibleTabIds();
      btn.classList.toggle('active', tabs[i] === tabId);
    });

    if (tabId === 'home') {
      renderHomeTab();
    } else {
      const def = TAB_DEFS[tabId];
      if (!def) return;
      const entry = findManifestEntry(def.microapp);
      if (!entry) {
        renderHomeTab();
        return;
      }
      loadAndRenderMicroApp(entry);
    }
  }

  function getVisibleTabIds() {
    return Object.entries(TAB_DEFS)
      .filter(([, def]) => !def.microapp || findManifestEntry(def.microapp))
      .map(([id]) => id);
  }

  // ========== Micro-App Loading (mirrors MicroAppService) ==========

  async function loadAndRenderMicroApp(entry) {
    // Show loading
    contentEl.innerHTML = `
      <div class="microapp-loading">
        <div class="spinner"></div>
        <p class="loading-text">Loading ${entry.name}...</p>
      </div>`;

    try {
      await loadMicroAppScript(entry);
      renderMicroAppElement(entry);
    } catch (err) {
      contentEl.innerHTML = `
        <div class="microapp-loading">
          <p class="loading-text" style="color: var(--danger);">Failed to load ${entry.name}</p>
          <p class="loading-text">${err.message}</p>
        </div>`;
    }
  }

  async function loadMicroAppScript(entry) {
    const state = scriptStates.get(entry.name);

    // Already loaded — just wait for element definition
    if (state && state.loaded) {
      await waitForElement(entry.elementTag);
      return;
    }

    // Currently loading — await existing promise
    if (state && state.loading) {
      await state.loading;
      return;
    }

    // Inject script
    const loadPromise = injectScript(entry);
    scriptStates.set(entry.name, { loaded: false, loading: loadPromise, error: null });

    try {
      await loadPromise;
      scriptStates.set(entry.name, { loaded: true, loading: null, error: null });
    } catch (err) {
      scriptStates.set(entry.name, { loaded: false, loading: null, error: err.message });
      throw err;
    }
  }

  function injectScript(entry) {
    return new Promise((resolve, reject) => {
      // Don't re-inject
      const existing = document.querySelector(`script[data-microapp="${entry.name}"]`);
      if (existing) {
        waitForElement(entry.elementTag).then(resolve).catch(reject);
        return;
      }

      const script = document.createElement('script');
      script.src = entry.remoteEntry;
      script.type = 'text/javascript';
      script.setAttribute('data-microapp', entry.name);
      script.setAttribute('data-version', entry.version);

      script.onload = () => {
        waitForElement(entry.elementTag).then(resolve).catch(reject);
      };

      script.onerror = () => {
        script.remove();
        reject(new Error(`Failed to load script for "${entry.name}"`));
      };

      document.head.appendChild(script);
    });
  }

  function waitForElement(tagName) {
    if (customElements.get(tagName)) return Promise.resolve();

    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error(`Timed out waiting for <${tagName}>`));
      }, 10000);

      customElements.whenDefined(tagName).then(() => {
        clearTimeout(timeout);
        resolve();
      }).catch(err => {
        clearTimeout(timeout);
        reject(err);
      });
    });
  }

  // ========== Render Micro-App Element ==========

  function renderMicroAppElement(entry) {
    contentEl.innerHTML = '';
    const el = document.createElement(entry.elementTag);
    el.setAttribute('account-id', 'acc-1001');
    el.setAttribute('data-version', entry.version);
    contentEl.appendChild(el);
    contentEl.classList.add('content-fade-in');
    setTimeout(() => contentEl.classList.remove('content-fade-in'), 300);
  }

  // ========== Home Tab ==========

  function renderHomeTab() {
    const data = window.MOCK_DATA;
    const hour = new Date().getHours();
    const timeOfDay = hour < 12 ? 'morning' : hour < 17 ? 'afternoon' : 'evening';

    contentEl.innerHTML = `
      <div class="content-fade-in">
        <!-- Greeting -->
        <div class="greeting">
          <p class="greeting-text">Good ${timeOfDay},</p>
          <h2 class="greeting-name">${data.account.name}</h2>
        </div>

        <!-- Balance Card -->
        <div class="balance-card">
          <div class="balance-label">Available Balance</div>
          <div class="balance-amount">${formatCurrency(data.account.balance)}</div>
          <div class="account-info">
            <div class="account-detail">
              <div class="label">Account</div>
              <div class="value">${data.account.id}</div>
            </div>
            <div class="account-detail">
              <div class="label">Type</div>
              <div class="value">${data.account.type}</div>
            </div>
            <div class="account-detail">
              <div class="label">Status</div>
              <div class="value">
                <ion-icon name="shield-checkmark-outline" style="font-size:14px;"></ion-icon>
                Active
              </div>
            </div>
          </div>
        </div>

        <!-- Quick Actions -->
        <div class="section-header"><h3>Quick Actions</h3></div>
        <div class="quick-actions">
          <div class="action-item"><ion-icon name="send-outline"></ion-icon><span>Transfer</span></div>
          <div class="action-item"><ion-icon name="wallet-outline"></ion-icon><span>Pay Bills</span></div>
          <div class="action-item"><ion-icon name="card-outline"></ion-icon><span>Cards</span></div>
          <div class="action-item"><ion-icon name="qr-code-outline"></ion-icon><span>Scan QR</span></div>
        </div>

        <!-- Recent Transactions -->
        <div class="section-header"><h3>Recent Transactions</h3><a href="#">View All</a></div>
        <div class="transactions-card">
          ${data.recentTransactions.map(txn => `
            <div class="txn-item">
              <div class="txn-icon-wrap">
                <ion-icon name="${txn.type === 'credit' ? 'arrow-down-outline' : 'arrow-up-outline'}" style="color: ${txn.type === 'credit' ? 'var(--success)' : 'var(--danger)'}"></ion-icon>
              </div>
              <div class="txn-info">
                <h4>${txn.description}</h4>
                <p>${txn.date}</p>
              </div>
              <span class="txn-amount ${txn.type}">${txn.type === 'credit' ? '+' : '-'}${formatCurrency(txn.amount)}</span>
            </div>
          `).join('')}
        </div>

        <!-- Insights Teaser -->
        <div class="insights-teaser">
          <ion-icon name="trending-up-outline"></ion-icon>
          <div>
            <strong>Spending Insight</strong>
            <p>Your spending is 12% lower than last month. Keep it up!</p>
          </div>
        </div>

        <div style="height: 16px;"></div>
      </div>
    `;
  }

  // ========== Helpers ==========

  function formatCurrency(amount) {
    return '$' + amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
})();
