// ============================================
// Micro-App: Statement Analysis (v1 + v2)
// Custom Element: <mf-statement-analysis>
// ============================================

class MfStatementAnalysis extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
    this._running = false;
  }

  connectedCallback() {
    this.render();
  }

  get isV2() {
    return this.getAttribute('data-version') === '2.0.0';
  }

  render() {
    const data = window.MOCK_DATA.analysis;
    const v2 = this.isV2;

    this.shadowRoot.innerHTML = `
      <style>${this.getStyles(v2)}</style>
      <div class="analysis-container${v2 ? ' v2' : ''}">
        <div class="header">
          <h2 class="title">
            ${v2 ? 'Statement Analysis v2' : 'Statement Analysis'}
            ${v2 ? '<span class="v2-badge">v2</span>' : ''}
          </h2>
          <button class="run-btn" id="run-btn">Run Analysis</button>
        </div>

        <div id="content-area">
          ${this.renderContent(data, v2)}
        </div>
      </div>
    `;

    this.shadowRoot.getElementById('run-btn').addEventListener('click', () => this.runAnalysis());
  }

  renderContent(data, v2) {
    return `
      <!-- Overview -->
      <div class="overview-row">
        <div class="overview-card">
          <div class="overview-label">Total Spend</div>
          <div class="overview-value expenses">${this.fmt(data.totalSpend)}</div>
        </div>
        <div class="overview-card">
          <div class="overview-label">Categories</div>
          <div class="overview-value">${data.categories.length}</div>
        </div>
        <div class="overview-card">
          <div class="overview-label">Insights</div>
          <div class="overview-value">${data.insights.length}</div>
        </div>
      </div>

      <!-- Category Table -->
      <div class="section">
        <h3 class="section-title">Spending by Category</h3>
        <table class="category-table">
          <thead>
            <tr><th>Category</th><th class="amount-col">Amount</th><th class="pct-col">%</th></tr>
          </thead>
          <tbody>
            ${data.categories.map(cat => `
              <tr>
                <td>${cat.category}</td>
                <td class="amount-col">${this.fmt(cat.total)}</td>
                <td class="pct-col">${cat.percentage.toFixed(1)}%</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>

      <!-- Top Merchants (v2 only) -->
      ${v2 ? `
        <div class="section">
          <h3 class="section-title">Top Merchants</h3>
          <div class="merchants-list">
            ${data.topMerchants.slice(0, 5).map((m, i) => `
              <div class="merchant-card">
                <div class="merchant-rank">#${i + 1}</div>
                <div class="merchant-info">
                  <div class="merchant-name">${m.merchant}</div>
                  <div class="merchant-count">${m.count} transaction${m.count > 1 ? 's' : ''}</div>
                </div>
                <div class="merchant-amount">${this.fmt(m.total)}</div>
              </div>
            `).join('')}
          </div>
        </div>
      ` : ''}

      <!-- Insights -->
      <div class="section">
        <h3 class="section-title">Insights</h3>
        <div class="insights-list">
          ${data.insights.map(ins => `
            <div class="insight-card insight-${ins.type}">
              <div class="insight-icon">${this.getIcon(ins.type)}</div>
              <div class="insight-desc">${ins.message}</div>
            </div>
          `).join('')}
        </div>
      </div>
    `;
  }

  runAnalysis() {
    if (this._running) return;
    this._running = true;
    const btn = this.shadowRoot.getElementById('run-btn');
    const area = this.shadowRoot.getElementById('content-area');
    btn.textContent = 'Analyzing...';
    btn.disabled = true;

    area.innerHTML = `
      <div style="display:flex; flex-direction:column; align-items:center; padding:40px;">
        <div class="spinner"></div>
        <p style="color:#999; font-size:13px;">Running analysis...</p>
      </div>
    `;

    setTimeout(() => {
      this._running = false;
      btn.textContent = 'Run Analysis';
      btn.disabled = false;
      area.innerHTML = this.renderContent(window.MOCK_DATA.analysis, this.isV2);
    }, 1500);
  }

  fmt(n) {
    return '$' + n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  getIcon(type) {
    const icons = {
      top_category: '\uD83D\uDCCA',
      travel_alert: '\u2708\uFE0F',
      health_alert: '\uD83C\uDFE5',
      everyday_tip: '\uD83D\uDED2',
      recurring: '\uD83D\uDD04',
    };
    return icons[type] || '\uD83D\uDCA1';
  }

  getStyles(v2) {
    const accent = v2 ? '#00897b' : '#3498db';
    const accentHover = v2 ? '#00796b' : '#2980b9';

    return `
      :host {
        display: block;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      }
      .analysis-container { padding: 16px; }
      .header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 18px;
      }
      .title {
        font-size: 20px;
        font-weight: 600;
        color: #1a1a2e;
        margin: 0;
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .v2-badge {
        background: #00897b;
        color: white;
        font-size: 10px;
        font-weight: 700;
        padding: 2px 7px;
        border-radius: 10px;
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }
      .run-btn {
        background: ${accent};
        color: white;
        border: none;
        padding: 8px 16px;
        border-radius: 8px;
        font-size: 12px;
        font-weight: 500;
        cursor: pointer;
        transition: background 0.2s;
        font-family: inherit;
      }
      .run-btn:hover { background: ${accentHover}; }
      .run-btn:disabled { background: #bdc3c7; cursor: not-allowed; }

      .spinner {
        width: 36px;
        height: 36px;
        border: 3px solid #e0e0e0;
        border-top-color: ${accent};
        border-radius: 50%;
        animation: spin 0.8s linear infinite;
        margin-bottom: 12px;
      }
      @keyframes spin { to { transform: rotate(360deg); } }

      /* Overview */
      .overview-row {
        display: flex;
        gap: 8px;
        margin-bottom: 20px;
      }
      .overview-card {
        flex: 1;
        background: #fff;
        border-radius: 10px;
        padding: 12px;
        box-shadow: 0 2px 8px rgba(0,0,0,0.08);
        ${v2 ? 'border-left: 3px solid #00897b;' : ''}
      }
      .overview-label {
        font-size: 10px;
        color: #7f8c8d;
        text-transform: uppercase;
        font-weight: 500;
        margin-bottom: 4px;
      }
      .overview-value {
        font-size: 18px;
        font-weight: 700;
        color: #2c3e50;
      }
      .overview-value.expenses { color: #e74c3c; }

      /* Sections */
      .section { margin-bottom: 20px; }
      .section-title {
        font-size: 14px;
        font-weight: 600;
        color: ${v2 ? '#00695c' : '#2c3e50'};
        margin: 0 0 10px 0;
      }

      /* Category Table */
      .category-table {
        width: 100%;
        border-collapse: collapse;
        background: #fff;
        border-radius: 10px;
        overflow: hidden;
        box-shadow: 0 2px 8px rgba(0,0,0,0.08);
      }
      .category-table th {
        text-align: left;
        padding: 10px 12px;
        font-size: 10px;
        font-weight: 500;
        color: #7f8c8d;
        text-transform: uppercase;
        background: #fafbfc;
        border-bottom: 2px solid #ecf0f1;
      }
      .category-table td {
        padding: 10px 12px;
        border-bottom: 1px solid #f5f5f5;
        color: #2c3e50;
        font-size: 12px;
      }
      .amount-col { text-align: right; }
      .pct-col { text-align: right; width: 50px; }

      /* Top Merchants (v2) */
      .merchants-list {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }
      .merchant-card {
        display: flex;
        align-items: center;
        background: #fff;
        border-radius: 10px;
        padding: 12px 14px;
        box-shadow: 0 2px 8px rgba(0,0,0,0.08);
        border-left: 3px solid #00897b;
        gap: 12px;
      }
      .merchant-rank {
        font-size: 14px;
        font-weight: 700;
        color: #00897b;
        min-width: 28px;
      }
      .merchant-info { flex: 1; }
      .merchant-name {
        font-size: 13px;
        font-weight: 600;
        color: #2c3e50;
      }
      .merchant-count {
        font-size: 10px;
        color: #95a5a6;
        margin-top: 1px;
      }
      .merchant-amount {
        font-size: 14px;
        font-weight: 600;
        color: #e74c3c;
      }

      /* Insights */
      .insights-list {
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .insight-card {
        display: flex;
        align-items: flex-start;
        background: #fff;
        border-radius: ${v2 ? '12px' : '10px'};
        padding: ${v2 ? '14px 16px' : '12px 14px'};
        box-shadow: 0 2px 8px rgba(0,0,0,0.08);
        gap: 12px;
      }
      .insight-top_category { border-left: 3px solid #3498db; }
      .insight-travel_alert { border-left: 3px solid #e74c3c; }
      .insight-health_alert { border-left: 3px solid #e74c3c; }
      .insight-recurring { border-left: 3px solid #f39c12; }
      .insight-icon {
        font-size: 18px;
        flex-shrink: 0;
        margin-top: 1px;
      }
      .insight-desc {
        font-size: 12px;
        color: #7f8c8d;
        line-height: 1.4;
      }
    `;
  }
}

customElements.define('mf-statement-analysis', MfStatementAnalysis);
