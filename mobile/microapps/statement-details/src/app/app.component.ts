import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../environments/environment';

interface Transaction {
  id: string;
  date: string;
  merchant: string;
  amount: number;
  category: string;
  description: string;
}

interface StatementSummary {
  statementId: string;
  periodStart: string;
  periodEnd: string;
  totalDebits: number;
  transactionCount: number;
}

interface StatementDetail {
  statementId: string;
  accountId: string;
  periodStart: string;
  periodEnd: string;
  totalDebits: number;
  totalCredits: number;
  transactions: Transaction[];
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="statement-details-container">
      <h2 class="title">Statement Details</h2>

      <div *ngIf="loading" class="loading">Loading statements...</div>
      <div *ngIf="error" class="error">{{ error }}</div>

      <div *ngIf="!loading && !error" class="statements-list">
        <div
          *ngFor="let stmt of statements"
          class="statement-card"
          [class.expanded]="expandedStatementId === stmt.statementId"
          (click)="toggleStatement(stmt)"
        >
          <div class="card-header">
            <div class="card-period">{{ stmt.periodStart }} — {{ stmt.periodEnd }}</div>
            <div class="card-chevron">{{ expandedStatementId === stmt.statementId ? '\u25B2' : '\u25BC' }}</div>
            <div class="card-meta">
              <span class="card-debits">{{ stmt.totalDebits | currency:'MYR':'RM ':'1.2-2' }} debited</span>
              <span class="card-dot">·</span>
              <span class="card-count">{{ stmt.transactionCount }} transactions</span>
            </div>
          </div>

          <div *ngIf="expandedStatementId === stmt.statementId" class="card-body" (click)="$event.stopPropagation()">
            <div *ngIf="transactionsLoading" class="loading-small">Loading transactions...</div>
            <div *ngIf="transactionsError" class="error-small">{{ transactionsError }}</div>

            <div *ngIf="!transactionsLoading && !transactionsError && transactions.length > 0" class="txn-list">
              <div *ngFor="let txn of transactions" class="txn-row">
                <div class="txn-main">
                  <div class="txn-merchant">{{ txn.merchant }}</div>
                  <div class="txn-meta">{{ txn.date }} · {{ txn.category }}</div>
                </div>
                <div class="txn-amount">-{{ txn.amount | currency:'MYR':'RM ':'1.2-2' }}</div>
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
    .statement-details-container {
      max-width: 700px;
      margin: 0 auto;
      padding: 16px;
    }
    .title {
      font-size: 22px;
      font-weight: 600;
      color: #1a1a2e;
      margin: 0 0 16px 0;
    }
    .loading, .error {
      padding: 20px;
      text-align: center;
      color: #666;
    }
    .error { color: #e74c3c; }
    .statement-card {
      background: #fff;
      border-radius: 10px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.08);
      margin-bottom: 12px;
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
      display: grid;
      grid-template-columns: 1fr auto;
      align-items: center;
      padding: 14px 18px;
      column-gap: 12px;
      row-gap: 4px;
    }
    .card-period {
      font-size: 15px;
      font-weight: 600;
      color: #2c3e50;
      min-width: 0;
      white-space: nowrap;
    }
    .card-chevron {
      font-size: 12px;
      color: #bdc3c7;
      justify-self: end;
    }
    .card-meta {
      grid-column: 1 / -1;
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      font-size: 12px;
      color: #7f8c8d;
      align-items: baseline;
    }
    .card-debits { color: #e74c3c; font-weight: 500; }
    .card-dot { color: #cdd1d6; }
    .card-count { color: #3498db; }
    .card-body {
      border-top: 1px solid #f0f0f0;
      padding: 16px 20px;
      background: #fafbfc;
    }
    .loading-small, .error-small {
      padding: 12px;
      text-align: center;
      font-size: 13px;
      color: #999;
    }
    .error-small { color: #e74c3c; }
    .txn-list {
      display: flex;
      flex-direction: column;
    }
    .txn-row {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 0;
      border-bottom: 1px solid #f5f5f5;
    }
    .txn-row:last-child { border-bottom: 0; }
    .txn-main { flex: 1; min-width: 0; }
    .txn-merchant {
      font-size: 14px;
      font-weight: 500;
      color: #2c3e50;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .txn-meta {
      font-size: 11px;
      color: #95a5a6;
      margin-top: 2px;
    }
    .txn-amount {
      flex: 0 0 auto;
      font-size: 14px;
      font-weight: 600;
      color: #e74c3c;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
  `]
})
export class AppComponent implements OnInit {
  private readonly baseUrl = `${environment.apiBaseUrl}/accounts/acc-1001/statements`;

  statements: StatementSummary[] = [];
  transactions: Transaction[] = [];
  loading = false;
  error: string | null = null;
  transactionsLoading = false;
  transactionsError: string | null = null;
  expandedStatementId: string | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadStatements();
  }

  loadStatements(): void {
    this.loading = true;
    this.error = null;
    this.http.get<StatementSummary[]>(this.baseUrl).subscribe({
      next: (data) => {
        this.statements = data;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load statements. Please try again.';
        this.loading = false;
        console.error('Error loading statements:', err);
      },
    });
  }

  toggleStatement(stmt: StatementSummary): void {
    if (this.expandedStatementId === stmt.statementId) {
      this.expandedStatementId = null;
      this.transactions = [];
      return;
    }

    this.expandedStatementId = stmt.statementId;
    this.loadTransactions(stmt.statementId);
  }

  loadTransactions(statementId: string): void {
    this.transactionsLoading = true;
    this.transactionsError = null;
    this.transactions = [];

    this.http.get<StatementDetail>(`${this.baseUrl}/${statementId}`).subscribe({
      next: (data) => {
        this.transactions = data.transactions || [];
        this.transactionsLoading = false;
      },
      error: (err) => {
        this.transactionsError = 'Failed to load transactions.';
        this.transactionsLoading = false;
        console.error('Error loading transactions:', err);
      },
    });
  }
}
