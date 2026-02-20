import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../environments/environment';

interface Recommendation {
  productId: string;
  productName: string;
  reason: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="recommendations-container">
      <div class="header">
        <h2 class="title">Recommendations</h2>
        <button class="refresh-btn" (click)="loadRecommendations()" [disabled]="loading">
          {{ loading ? 'Loading...' : 'Refresh' }}
        </button>
      </div>

      <div *ngIf="loading" class="loading">Loading recommendations...</div>
      <div *ngIf="error" class="error">{{ error }}</div>

      <div *ngIf="!loading && !error && recommendations.length === 0" class="empty">
        No recommendations available at this time.
      </div>

      <div *ngIf="!loading && !error" class="recommendations-list">
        <div *ngFor="let rec of recommendations" class="recommendation-card">
          <div class="rec-icon">{{ getProductIcon(rec.productId) }}</div>
          <div class="rec-content">
            <div class="rec-name">{{ rec.productName }}</div>
            <div class="rec-reason">{{ rec.reason }}</div>
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
    .recommendations-container {
      max-width: 700px;
      margin: 0 auto;
      padding: 16px;
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
    }
    .refresh-btn {
      background: #8e44ad;
      color: white;
      border: none;
      padding: 10px 20px;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 500;
      cursor: pointer;
      transition: background 0.2s;
    }
    .refresh-btn:hover { background: #7d3c98; }
    .refresh-btn:disabled { background: #bdc3c7; cursor: not-allowed; }
    .loading, .error, .empty {
      padding: 24px;
      text-align: center;
      color: #666;
      font-size: 14px;
    }
    .error { color: #e74c3c; }
    .empty { color: #95a5a6; }

    .recommendations-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    .recommendation-card {
      display: flex;
      align-items: flex-start;
      background: #fff;
      border-radius: 12px;
      padding: 18px 20px;
      box-shadow: 0 2px 10px rgba(0,0,0,0.08);
      gap: 16px;
      transition: box-shadow 0.2s, transform 0.2s;
    }
    .recommendation-card:hover {
      box-shadow: 0 4px 18px rgba(0,0,0,0.12);
      transform: translateY(-1px);
    }
    .rec-icon {
      font-size: 32px;
      flex-shrink: 0;
      width: 50px;
      height: 50px;
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
      font-size: 16px;
      font-weight: 600;
      color: #2c3e50;
      margin-bottom: 2px;
    }
    .rec-reason {
      font-size: 13px;
      color: #7f8c8d;
      line-height: 1.4;
    }
  `]
})
export class AppComponent implements OnInit {
  private readonly apiUrl =
    `${environment.apiBaseUrl}/accounts/acc-1001/recommendations?statementId=stmt-2025-12`;

  recommendations: Recommendation[] = [];
  loading = false;
  error: string | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadRecommendations();
  }

  loadRecommendations(): void {
    this.loading = true;
    this.error = null;

    this.http.get<Recommendation[]>(this.apiUrl).subscribe({
      next: (data) => {
        this.recommendations = data;
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load recommendations. Please try again.';
        this.loading = false;
        console.error('Error loading recommendations:', err);
      },
    });
  }

  getProductIcon(productId: string): string {
    switch (productId) {
      case 'travel_rewards_card':
        return '\u2708\uFE0F';
      case 'health_insurance_basic':
        return '\uD83D\uDEE1\uFE0F';
      case 'cashback_everyday_card':
        return '\uD83D\uDCB3';
      case 'bill_pay_assist':
        return '\uD83D\uDCB0';
      default:
        return '\u2B50';
    }
  }
}
