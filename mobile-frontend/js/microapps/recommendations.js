// ============================================
// Micro-App: Recommendations
// Custom Element: <mf-recommendations>
// ============================================

class MfRecommendations extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
    this._loading = false;
  }

  connectedCallback() {
    this.render();
  }

  render() {
    const recs = window.MOCK_DATA.recommendations;

    this.shadowRoot.innerHTML = `
      <style>${this.getStyles()}</style>
      <div class="recommendations-container">
        <div class="header">
          <h2 class="title">Recommendations</h2>
          <button class="refresh-btn" id="refresh-btn">Refresh</button>
        </div>
        <div id="content-area">
          ${this.renderList(recs)}
        </div>
      </div>
    `;

    this.shadowRoot.getElementById('refresh-btn').addEventListener('click', () => this.refresh());
  }

  renderList(recs) {
    if (!recs || recs.length === 0) {
      return '<div class="empty">No recommendations available at this time.</div>';
    }
    return `
      <div class="recommendations-list">
        ${recs.map(rec => `
          <div class="recommendation-card">
            <div class="rec-icon">${this.getIcon(rec.productId)}</div>
            <div class="rec-content">
              <div class="rec-name">${rec.productName}</div>
              <div class="rec-reason">${rec.reason}</div>
            </div>
          </div>
        `).join('')}
      </div>
    `;
  }

  refresh() {
    if (this._loading) return;
    this._loading = true;
    const btn = this.shadowRoot.getElementById('refresh-btn');
    const area = this.shadowRoot.getElementById('content-area');
    btn.textContent = 'Loading...';
    btn.disabled = true;

    area.innerHTML = `
      <div style="display:flex; flex-direction:column; align-items:center; padding:40px;">
        <div class="spinner"></div>
        <p style="color:#999; font-size:13px;">Fetching recommendations...</p>
      </div>
    `;

    setTimeout(() => {
      this._loading = false;
      btn.textContent = 'Refresh';
      btn.disabled = false;
      area.innerHTML = this.renderList(window.MOCK_DATA.recommendations);
    }, 1200);
  }

  getIcon(productId) {
    const icons = {
      travel_rewards_card: '\u2708\uFE0F',
      health_insurance_basic: '\uD83D\uDEE1\uFE0F',
      cashback_everyday_card: '\uD83D\uDCB3',
      bill_pay_assist: '\uD83D\uDCB0',
    };
    return icons[productId] || '\u2B50';
  }

  getStyles() {
    return `
      :host {
        display: block;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      }
      .recommendations-container { padding: 16px; }
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
      }
      .refresh-btn {
        background: #8e44ad;
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
      .refresh-btn:hover { background: #7d3c98; }
      .refresh-btn:disabled { background: #bdc3c7; cursor: not-allowed; }

      .spinner {
        width: 36px;
        height: 36px;
        border: 3px solid #e0e0e0;
        border-top-color: #8e44ad;
        border-radius: 50%;
        animation: spin 0.8s linear infinite;
        margin-bottom: 12px;
      }
      @keyframes spin { to { transform: rotate(360deg); } }

      .empty {
        padding: 24px;
        text-align: center;
        color: #95a5a6;
        font-size: 13px;
      }
      .recommendations-list {
        display: flex;
        flex-direction: column;
        gap: 10px;
      }
      .recommendation-card {
        display: flex;
        align-items: flex-start;
        background: #fff;
        border-radius: 12px;
        padding: 16px 18px;
        box-shadow: 0 2px 10px rgba(0,0,0,0.08);
        gap: 14px;
        transition: box-shadow 0.2s, transform 0.2s;
      }
      .recommendation-card:hover {
        box-shadow: 0 4px 18px rgba(0,0,0,0.12);
        transform: translateY(-1px);
      }
      .rec-icon {
        font-size: 28px;
        flex-shrink: 0;
        width: 46px;
        height: 46px;
        display: flex;
        align-items: center;
        justify-content: center;
        background: #f5f0ff;
        border-radius: 12px;
      }
      .rec-content {
        flex: 1;
        min-width: 0;
      }
      .rec-name {
        font-size: 14px;
        font-weight: 600;
        color: #2c3e50;
        margin-bottom: 3px;
      }
      .rec-reason {
        font-size: 12px;
        color: #7f8c8d;
        line-height: 1.4;
      }
    `;
  }
}

customElements.define('mf-recommendations', MfRecommendations);
