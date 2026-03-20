// ============================================
// Micro-App: Statement Details
// Custom Element: <mf-statement-details>
// ============================================

class MfStatementDetails extends HTMLElement {
  constructor() {
    super();
    this.attachShadow({ mode: 'open' });
    this._expandedId = null;
  }

  connectedCallback() {
    // Simulate network fetch latency (5G: 800–1500ms)
    this.showLoading();
    const delay = 800 + Math.random() * 700;
    setTimeout(() => this.render(), delay);
  }

  showLoading() {
    this.shadowRoot.innerHTML = `
      <style>
        :host { display: block; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
        .loading { display: flex; flex-direction: column; align-items: center; padding: 60px 24px; }
        .spinner { width: 36px; height: 36px; border: 3px solid #e0e0e0; border-top-color: #0f3460; border-radius: 50%; animation: spin 0.8s linear infinite; margin-bottom: 12px; }
        @keyframes spin { to { transform: rotate(360deg); } }
        .loading-text { color: #92949c; font-size: 13px; }
      </style>
      <div class="loading">
        <div class="spinner"></div>
        <p class="loading-text">Fetching statements...</p>
      </div>
    `;
  }

  render() {
    const statements = window.MOCK_DATA.statements;

    this.shadowRoot.innerHTML = `
      <style>${this.getStyles()}</style>
      <div class="statement-details-container">
        <h2 class="title">Statement Details</h2>
        <div class="statements-list">
          ${statements.map(stmt => `
            <div class="statement-card" data-id="${stmt.statementId}">
              <div class="card-header">
                <div class="card-period">${stmt.periodStart} — ${stmt.periodEnd}</div>
                <div class="card-meta">
                  <span class="card-debits">Debits: ${this.fmt(stmt.totalDebits)}</span>
                  <span class="card-count">${stmt.transactionCount} transactions</span>
                </div>
                <div class="card-chevron">\u25BC</div>
              </div>
              <div class="card-body" style="display:none;">
                <table class="transactions-table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Merchant</th>
                      <th>Category</th>
                      <th class="amount-col">Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${stmt.transactions.map(txn => `
                      <tr>
                        <td>${txn.date}</td>
                        <td>${txn.merchant}</td>
                        <td>${txn.category}</td>
                        <td class="amount-col negative">-${this.fmt(txn.amount)}</td>
                      </tr>
                    `).join('')}
                  </tbody>
                </table>
              </div>
            </div>
          `).join('')}
        </div>
      </div>
    `;

    // Attach click handlers
    this.shadowRoot.querySelectorAll('.statement-card').forEach(card => {
      const header = card.querySelector('.card-header');
      header.addEventListener('click', () => this.toggleCard(card));
    });
  }

  toggleCard(card) {
    const id = card.dataset.id;
    const body = card.querySelector('.card-body');
    const chevron = card.querySelector('.card-chevron');
    const isExpanded = card.classList.contains('expanded');

    // Collapse all
    this.shadowRoot.querySelectorAll('.statement-card').forEach(c => {
      c.classList.remove('expanded');
      c.querySelector('.card-body').style.display = 'none';
      c.querySelector('.card-chevron').textContent = '\u25BC';
    });

    // Expand if wasn't already open
    if (!isExpanded) {
      card.classList.add('expanded');
      body.style.display = 'block';
      chevron.textContent = '\u25B2';
    }
  }

  fmt(n) {
    return '$' + n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  getStyles() {
    return `
      :host {
        display: block;
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      }
      .statement-details-container {
        padding: 16px;
      }
      .title {
        font-size: 20px;
        font-weight: 600;
        color: #1a1a2e;
        margin: 0 0 16px 0;
      }
      .statements-list {
        display: flex;
        flex-direction: column;
        gap: 10px;
      }
      .statement-card {
        background: #fff;
        border-radius: 10px;
        box-shadow: 0 2px 8px rgba(0,0,0,0.08);
        cursor: pointer;
        transition: box-shadow 0.2s;
        overflow: hidden;
      }
      .statement-card:hover {
        box-shadow: 0 4px 16px rgba(0,0,0,0.12);
      }
      .statement-card.expanded {
        box-shadow: 0 4px 20px rgba(0,0,0,0.15);
      }
      .card-header {
        display: flex;
        align-items: center;
        padding: 14px 16px;
        gap: 10px;
      }
      .card-period {
        font-size: 14px;
        font-weight: 600;
        color: #2c3e50;
        flex: 0 0 auto;
      }
      .card-meta {
        flex: 1;
        display: flex;
        gap: 12px;
        font-size: 11px;
        color: #7f8c8d;
      }
      .card-debits { color: #e74c3c; font-weight: 500; }
      .card-count { color: #3498db; }
      .card-chevron {
        font-size: 10px;
        color: #bdc3c7;
      }
      .card-body {
        border-top: 1px solid #f0f0f0;
        padding: 12px 16px;
        background: #fafbfc;
        cursor: default;
      }
      .transactions-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 11px;
      }
      .transactions-table th {
        text-align: left;
        padding: 6px 8px;
        color: #7f8c8d;
        font-weight: 500;
        border-bottom: 2px solid #ecf0f1;
        font-size: 10px;
        text-transform: uppercase;
      }
      .transactions-table td {
        padding: 8px 8px;
        border-bottom: 1px solid #f5f5f5;
        color: #2c3e50;
      }
      .amount-col { text-align: right; }
      .negative { color: #e74c3c; font-weight: 500; }
    `;
  }
}

customElements.define('mf-statement-details', MfStatementDetails);
