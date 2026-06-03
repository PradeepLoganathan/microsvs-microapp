import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../environments/environment';

interface CategoryTotal {
  category: string;
  total: number;
  count: number;
  percentage: number;
}

interface Insight {
  type: string;
  message: string;
}

interface MerchantTotal {
  merchant: string;
  total: number;
  count: number;
}

interface AnalysisSummary {
  accountId: string;
  statementId: string;
  totalSpend: number;
  categories: CategoryTotal[];
  topMerchants: MerchantTotal[];
  insights: Insight[];
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="analysis-container" [class.v2]="isV2">
      <div class="header">
        <h2 class="title">
          {{ isV2 ? 'Statement Analysis v2' : 'Statement Analysis' }}
          <span *ngIf="isV2" class="v2-badge">v2</span>
        </h2>
        <button class="run-btn" (click)="runAnalysis()" [disabled]="runningAnalysis">
          {{ runningAnalysis ? 'Analyzing...' : 'Run Analysis' }}
        </button>
      </div>

      <div *ngIf="loading" class="loading">Loading analysis summary...</div>
      <div *ngIf="error" class="error">{{ error }}</div>

      <div *ngIf="!loading && !error && summary">
        <!-- Totals Overview -->
        <div class="overview-row">
          <div class="overview-card">
            <div class="overview-label">Total Spend</div>
            <div class="overview-value expenses">{{ summary.totalSpend | currency:'MYR':'RM ':'1.2-2' }}</div>
          </div>
          <div class="overview-card">
            <div class="overview-label">Categories</div>
            <div class="overview-value">{{ summary.categories.length }}</div>
          </div>
          <div class="overview-card">
            <div class="overview-label">Insights</div>
            <div class="overview-value">{{ summary.insights.length }}</div>
          </div>
        </div>

        <!-- Category Breakdown -->
        <div class="section">
          <h3 class="section-title">Spending by Category</h3>
          <table class="category-table">
            <thead>
              <tr>
                <th>Category</th>
                <th class="amount-col">Amount</th>
                <th class="pct-col">%</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let cat of summary.categories">
                <td>{{ cat.category }}</td>
                <td class="amount-col">{{ cat.total | currency:'MYR':'RM ':'1.2-2' }}</td>
                <td class="pct-col">{{ cat.percentage | number:'1.1-1' }}%</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Top Merchants (v2 only) -->
        <div *ngIf="isV2 && summary.topMerchants && summary.topMerchants.length > 0" class="section">
          <h3 class="section-title">Top Merchants</h3>
          <div class="merchants-list">
            <div *ngFor="let merchant of summary.topMerchants.slice(0, 5); let i = index" class="merchant-card">
              <div class="merchant-rank">#{{ i + 1 }}</div>
              <div class="merchant-info">
                <div class="merchant-name">{{ merchant.merchant }}</div>
                <div class="merchant-count">{{ merchant.count }} transactions</div>
              </div>
              <div class="merchant-amount">{{ merchant.total | currency:'MYR':'RM ':'1.2-2' }}</div>
            </div>
          </div>
        </div>

        <!-- Insights -->
        <div *ngIf="summary.insights && summary.insights.length > 0" class="section">
          <h3 class="section-title">Insights</h3>
          <div class="insights-list">
            <div *ngFor="let insight of summary.insights" class="insight-card" [class]="'insight-' + insight.type">
              <div class="insight-icon">
                {{ getInsightIcon(insight.type) }}
              </div>
              <div class="insight-content">
                <div class="insight-desc">{{ insight.message }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }
    .analysis-container {
      max-width: 700px;
      margin: 0 auto;
      padding: 16px;
    }
    .analysis-container.v2 {
      max-width: 800px;
    }
    .header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 20px;
    }
    .title {
      font-size: 22px;
      font-weight: 600;
      color: #1a1a2e;
      margin: 0;
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .v2-badge {
      background: #00897b;
      color: white;
      font-size: 11px;
      font-weight: 700;
      padding: 3px 8px;
      border-radius: 12px;
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    .run-btn {
      background: #3498db;
      color: white;
      border: none;
      padding: 10px 20px;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      transition: background 0.2s;
    }
    .run-btn:hover { background: #2980b9; }
    .run-btn:disabled { background: #bdc3c7; cursor: not-allowed; }
    .v2 .run-btn { background: #00897b; }
    .v2 .run-btn:hover { background: #00796b; }
    .loading, .error {
      padding: 20px;
      text-align: center;
      color: #666;
    }
    .error { color: #e74c3c; }

    /* Overview Cards */
    .overview-row {
      display: flex;
      gap: 12px;
      margin-bottom: 24px;
    }
    .overview-card {
      flex: 1;
      background: #fff;
      border-radius: 10px;
      padding: 16px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.08);
    }
    .v2 .overview-card {
      border-left: 4px solid #00897b;
    }
    .overview-label {
      font-size: 12px;
      color: #7f8c8d;
      text-transform: uppercase;
      font-weight: 500;
      margin-bottom: 6px;
    }
    .overview-value {
      font-size: 20px;
      font-weight: 700;
      color: #2c3e50;
    }
    .overview-value.income { color: #27ae60; }
    .overview-value.expenses { color: #e74c3c; }
    .overview-value.positive { color: #27ae60; }
    .overview-value.negative { color: #e74c3c; }

    /* Sections */
    .section {
      margin-bottom: 24px;
    }
    .section-title {
      font-size: 16px;
      font-weight: 600;
      color: #2c3e50;
      margin: 0 0 12px 0;
    }
    .v2 .section-title {
      color: #00695c;
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
      padding: 12px 16px;
      font-size: 12px;
      font-weight: 500;
      color: #7f8c8d;
      text-transform: uppercase;
      background: #fafbfc;
      border-bottom: 2px solid #ecf0f1;
    }
    .category-table td {
      padding: 12px 16px;
      border-bottom: 1px solid #f5f5f5;
      color: #2c3e50;
      font-size: 14px;
    }
    .amount-col { text-align: right; }
    .pct-col { text-align: right; width: 60px; }

    /* Top Merchants (v2) */
    .merchants-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .merchant-card {
      display: flex;
      align-items: center;
      background: #fff;
      border-radius: 10px;
      padding: 14px 18px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.08);
      border-left: 4px solid #00897b;
      gap: 14px;
    }
    .merchant-rank {
      font-size: 16px;
      font-weight: 700;
      color: #00897b;
      min-width: 30px;
    }
    .merchant-info {
      flex: 1;
    }
    .merchant-name {
      font-size: 15px;
      font-weight: 600;
      color: #2c3e50;
    }
    .merchant-count {
      font-size: 12px;
      color: #95a5a6;
      margin-top: 2px;
    }
    .merchant-amount {
      font-size: 16px;
      font-weight: 600;
      color: #e74c3c;
    }

    /* Insights */
    .insights-list {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }
    .insight-card {
      display: flex;
      align-items: flex-start;
      background: #fff;
      border-radius: 10px;
      padding: 14px 18px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.08);
      gap: 14px;
    }
    .v2 .insight-card {
      border-radius: 12px;
      padding: 18px 22px;
    }
    .insight-card.insight-high {
      border-left: 4px solid #e74c3c;
    }
    .insight-card.insight-medium {
      border-left: 4px solid #f39c12;
    }
    .insight-card.insight-low {
      border-left: 4px solid #3498db;
    }
    .insight-card.insight-info {
      border-left: 4px solid #3498db;
    }
    .insight-icon {
      font-size: 20px;
      flex-shrink: 0;
      margin-top: 2px;
    }
    .insight-content {
      flex: 1;
    }
    .insight-title {
      font-size: 15px;
      font-weight: 600;
      color: #2c3e50;
      margin-bottom: 4px;
    }
    .insight-desc {
      font-size: 13px;
      color: #7f8c8d;
      line-height: 1.4;
    }
  `]
})
export class AppComponent implements OnInit {
  // Account context pushed in by the shell via the `account-id` attribute.
  private _accountId = 'acc-1001';
  private _statementId: string | null = null;
  private ready = false;

  @Input() set accountId(value: string) {
    if (!value || value === this._accountId) return;
    this._accountId = value;
    if (this.ready) this.resolveStatementThenLoad();
  }

  private get baseUrl(): string {
    return `${environment.apiBaseUrl}/accounts/${this._accountId}/analysis`;
  }

  isV2 = environment.version === 2;
  summary: AnalysisSummary | null = null;
  loading = false;
  error: string | null = null;
  runningAnalysis = false;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.ready = true;
    this.resolveStatementThenLoad();
  }

  /**
   * Pick the statement to analyse for the active account — the curated demo
   * statement if the account has it, otherwise its latest — then load the summary.
   */
  private resolveStatementThenLoad(): void {
    this.summary = null;
    this.error = null;
    const acct = this._accountId;
    this.http.get<{ statementId: string; periodEnd: string; transactionCount: number }[]>(
      `${environment.statementApiUrl}/accounts/${acct}/statements`)
      .subscribe({
        next: (summaries) => {
          if (acct !== this._accountId) return; // superseded by a newer switch
          if (!summaries || summaries.length === 0) {
            this._statementId = null;
            this.error = 'No statements to analyse for this account yet.';
            return;
          }
          // Ignore empty statements (e.g. the opening statement) so analysis runs
          // on a month with real activity.
          const withTxns = summaries.filter((s) => s.transactionCount > 0);
          const pool = withTxns.length ? withTxns : summaries;
          const preferred = pool.find(s => s.statementId === environment.preferredStatementId);
          const latest = pool.slice().sort((a, b) => b.periodEnd.localeCompare(a.periodEnd))[0];
          this._statementId = (preferred ?? latest).statementId;
          this.loadSummary();
        },
        error: (err) => {
          this.error = 'Failed to load statements for analysis.';
          console.error('Error resolving statement:', err);
        },
      });
  }

  runAnalysis(): void {
    if (!this._statementId) return;
    this.runningAnalysis = true;
    this.error = null;

    this.http.post(`${this.baseUrl}/run?statementId=${this._statementId}`, {}).subscribe({
      next: () => {
        this.runningAnalysis = false;
        this.loadSummary();
      },
      error: (err) => {
        this.runningAnalysis = false;
        this.error = 'Failed to run analysis. Please try again.';
        console.error('Error running analysis:', err);
      },
    });
  }

  loadSummary(): void {
    if (!this._statementId) return;
    this.loading = true;
    this.error = null;

    this.http.get<AnalysisSummary>(`${this.baseUrl}/summary?statementId=${this._statementId}`).subscribe({
      next: (data) => {
        this.summary = data;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load analysis summary. Try running the analysis first.';
        this.loading = false;
        console.error('Error loading summary:', err);
      },
    });
  }

  getInsightIcon(type: string): string {
    switch (type) {
      case 'top_category': return '\uD83D\uDCCA';
      case 'travel_alert': return '\u2708\uFE0F';
      case 'health_alert': return '\uD83C\uDFE5';
      case 'everyday_tip': return '\uD83D\uDED2';
      case 'recurring': return '\uD83D\uDD04';
      default: return '\uD83D\uDCA1';
    }
  }
}
