// ============================================
// Configuration Panel — Manifest Editor
// Works standalone via BroadcastChannel
// ============================================

(function () {
  'use strict';

  const actionsEl = document.getElementById('action-buttons');
  const manifestEl = document.getElementById('manifest-json');
  const eventLogEl = document.getElementById('event-log');
  const toastContainer = document.getElementById('toast-container');
  const statusEl = document.getElementById('connection-status');

  // --- Local manifest state (synced with phone via BroadcastChannel) ---
  const DEFAULT_MANIFEST = JSON.parse(JSON.stringify(window.MOCK_DATA.defaultManifest));
  let currentManifest = JSON.parse(JSON.stringify(DEFAULT_MANIFEST));
  let phoneConnected = false;

  // --- BroadcastChannel ---
  const channel = new BroadcastChannel('manifest-sync');

  channel.onmessage = (event) => {
    const { type, manifest, activeTab } = event.data;
    if (type === 'state-sync' && manifest) {
      // Phone responded with its current state — sync our local copy
      currentManifest = JSON.parse(JSON.stringify(manifest));
      setConnected(true);
      renderActions();
      renderManifest();
    }
  };

  // Ask phone for its current state on load
  channel.postMessage({ type: 'request-state' });

  // Periodically ping to detect connection status
  setInterval(() => {
    phoneConnected = false;
    channel.postMessage({ type: 'request-state' });
    // If no state-sync comes back within 500ms, mark disconnected
    setTimeout(() => {
      if (!phoneConnected) setConnected(false);
    }, 500);
  }, 3000);

  // --- Action definitions ---
  const ACTIONS = [
    {
      id: 'remove-recs',
      label: 'Remove Recommendations',
      icon: '\u2796',
      cls: 'danger',
      enabled: () => hasEntry('recommendations'),
      run: () => {
        currentManifest.microfrontends = currentManifest.microfrontends.filter(e => e.name !== 'recommendations');
        apply('Removed recommendations micro-app', 'remove');
      },
    },
    {
      id: 'add-recs',
      label: 'Add Recommendations',
      icon: '\u2795',
      cls: 'success',
      enabled: () => !hasEntry('recommendations'),
      run: () => {
        const def = DEFAULT_MANIFEST.microfrontends.find(e => e.name === 'recommendations');
        currentManifest.microfrontends.push(JSON.parse(JSON.stringify(def)));
        apply('Added recommendations micro-app', 'add');
      },
    },
    {
      id: 'v2-analysis',
      label: 'Switch Analysis to v2',
      icon: '\u2B06\uFE0F',
      cls: 'info',
      enabled: () => {
        const e = getEntry('statement-analysis');
        return e && e.version === '1.0.0';
      },
      run: () => {
        const e = currentManifest.microfrontends.find(x => x.name === 'statement-analysis');
        if (e) e.version = '2.0.0';
        apply('Switched analysis to v2.0.0', 'update');
      },
    },
    {
      id: 'v1-analysis',
      label: 'Switch Analysis to v1',
      icon: '\u2B07\uFE0F',
      cls: 'info',
      enabled: () => {
        const e = getEntry('statement-analysis');
        return e && e.version === '2.0.0';
      },
      run: () => {
        const e = currentManifest.microfrontends.find(x => x.name === 'statement-analysis');
        if (e) e.version = '1.0.0';
        apply('Switched analysis back to v1.0.0', 'update');
      },
    },
    {
      id: 'remove-all',
      label: 'Remove All Micro-Apps',
      icon: '\uD83D\uDDD1\uFE0F',
      cls: 'danger',
      enabled: () => currentManifest.microfrontends.length > 0,
      run: () => {
        currentManifest.microfrontends = [];
        apply('Removed all micro-apps', 'remove');
      },
    },
    {
      id: 'restore',
      label: 'Restore Default',
      icon: '\u267B\uFE0F',
      cls: 'reset',
      enabled: () => true,
      run: () => {
        currentManifest = JSON.parse(JSON.stringify(DEFAULT_MANIFEST));
        apply('Restored default manifest (3 micro-apps)', 'reset');
      },
    },
  ];

  // --- Initialize ---
  renderActions();
  renderManifest();
  addLogEntry('Config panel initialized', 'reset');

  // --- Core ---

  function apply(message, logType) {
    // Broadcast manifest to phone view
    channel.postMessage({
      type: 'manifest-update',
      manifest: JSON.parse(JSON.stringify(currentManifest)),
    });

    renderActions();
    renderManifest();
    addLogEntry(message, logType);
    showToast(message, logType);
  }

  function hasEntry(name) {
    return !!getEntry(name);
  }

  function getEntry(name) {
    return currentManifest.microfrontends.find(e => e.name === name);
  }

  // --- Connection Status ---

  function setConnected(connected) {
    phoneConnected = connected;
    if (!statusEl) return;
    statusEl.className = 'connection-status ' + (connected ? 'connected' : 'disconnected');
    const textEl = statusEl.querySelector('.status-text');
    if (textEl) {
      textEl.textContent = connected ? 'Phone view connected' : 'Phone view not connected';
    }
  }

  // --- Render Actions ---

  function renderActions() {
    actionsEl.innerHTML = ACTIONS.map(a => {
      const enabled = a.enabled();
      return `
        <button class="action-btn ${a.cls}" data-action="${a.id}" ${enabled ? '' : 'disabled'}>
          <span class="btn-icon">${a.icon}</span>
          ${a.label}
        </button>
      `;
    }).join('');

    actionsEl.querySelectorAll('.action-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const action = ACTIONS.find(a => a.id === btn.dataset.action);
        if (action && action.enabled()) action.run();
      });
    });
  }

  // --- Render Manifest JSON ---

  function renderManifest() {
    const json = JSON.stringify(currentManifest, null, 2);
    manifestEl.innerHTML = `<code>${syntaxHighlight(json)}</code>`;
  }

  function syntaxHighlight(json) {
    return json
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"([^"]+)"(?=\s*:)/g, '<span class="json-key">"$1"</span>')
      .replace(/:\s*"([^"]*)"/g, ': <span class="json-string">"$1"</span>')
      .replace(/:\s*(\d+\.?\d*)/g, ': <span class="json-number">$1</span>')
      .replace(/[{}\[\]]/g, '<span class="json-bracket">$&</span>');
  }

  // --- Event Log ---

  function addLogEntry(message, type) {
    const now = new Date();
    const time = now.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const entry = document.createElement('div');
    entry.className = `log-entry log-${type}`;
    entry.innerHTML = `<span class="log-time">${time}</span><span class="log-msg">${message}</span>`;
    eventLogEl.insertBefore(entry, eventLogEl.firstChild);

    // Keep max 20 entries
    while (eventLogEl.children.length > 20) {
      eventLogEl.removeChild(eventLogEl.lastChild);
    }
  }

  // --- Toast ---

  function showToast(message, type) {
    const toastType = {
      add: 'toast-success',
      remove: 'toast-danger',
      update: 'toast-info',
      reset: 'toast-warning',
    }[type] || 'toast-info';

    const toast = document.createElement('div');
    toast.className = `toast ${toastType}`;
    toast.textContent = message;
    toastContainer.appendChild(toast);

    setTimeout(() => {
      toast.classList.add('toast-out');
      setTimeout(() => toast.remove(), 300);
    }, 2500);
  }
})();
