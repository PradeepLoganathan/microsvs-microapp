import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  IonHeader,
  IonToolbar,
  IonTitle,
  IonContent,
  IonCard,
  IonCardHeader,
  IonCardTitle,
  IonCardContent,
  IonList,
  IonItem,
  IonLabel,
  IonIcon,
  IonBadge,
  IonNote,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  walletOutline,
  sendOutline,
  cardOutline,
  qrCodeOutline,
  arrowUpOutline,
  arrowDownOutline,
  trendingUpOutline,
  shieldCheckmarkOutline,
} from 'ionicons/icons';

interface Transaction {
  description: string;
  amount: number;
  date: string;
  type: 'credit' | 'debit';
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule,
    IonHeader,
    IonToolbar,
    IonTitle,
    IonContent,
    IonCard,
    IonCardHeader,
    IonCardTitle,
    IonCardContent,
    IonList,
    IonItem,
    IonLabel,
    IonIcon,
    IonBadge,
    IonNote,
  ],
  template: `
    <ion-header>
      <ion-toolbar>
        <ion-title>Banking</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content>
      <!-- Greeting -->
      <div class="greeting">
        <p class="greeting-text">Good {{ timeOfDay }},</p>
        <h2 class="greeting-name">{{ account.name }}</h2>
      </div>

      <!-- Balance Card -->
      <ion-card class="balance-card">
        <ion-card-content>
          <div class="balance-label">Available Balance</div>
          <div class="balance-amount">
            {{ account.balance | currency: 'USD' : 'symbol' : '1.2-2' }}
          </div>
          <div class="account-info">
            <div class="account-detail">
              <div class="label">Account</div>
              <div class="value">{{ account.id }}</div>
            </div>
            <div class="account-detail">
              <div class="label">Type</div>
              <div class="value">{{ account.type }}</div>
            </div>
            <div class="account-detail">
              <div class="label">Status</div>
              <div class="value">
                <ion-icon name="shield-checkmark-outline"></ion-icon>
                Active
              </div>
            </div>
          </div>
        </ion-card-content>
      </ion-card>

      <!-- Quick Actions -->
      <div class="section-header">
        <h3>Quick Actions</h3>
      </div>
      <div class="quick-actions">
        <div class="action-item">
          <ion-icon name="send-outline"></ion-icon>
          <span>Transfer</span>
        </div>
        <div class="action-item">
          <ion-icon name="wallet-outline"></ion-icon>
          <span>Pay Bills</span>
        </div>
        <div class="action-item">
          <ion-icon name="card-outline"></ion-icon>
          <span>Cards</span>
        </div>
        <div class="action-item">
          <ion-icon name="qr-code-outline"></ion-icon>
          <span>Scan QR</span>
        </div>
      </div>

      <!-- Recent Transactions -->
      <div class="section-header">
        <h3>Recent Transactions</h3>
        <a href="javascript:void(0)">View All</a>
      </div>
      <ion-card class="transactions-card">
        <ion-list lines="full">
          @for (txn of recentTransactions; track txn.description) {
            <ion-item>
              <div class="txn-icon" slot="start">
                <ion-icon
                  [name]="txn.type === 'credit' ? 'arrow-down-outline' : 'arrow-up-outline'"
                  [style.color]="txn.type === 'credit' ? '#16c784' : '#ea3943'"
                ></ion-icon>
              </div>
              <ion-label>
                <h3>{{ txn.description }}</h3>
                <p>{{ txn.date }}</p>
              </ion-label>
              <ion-note
                slot="end"
                [style.color]="txn.type === 'credit' ? '#16c784' : '#ea3943'"
                style="font-weight: 600; font-size: 0.9rem;"
              >
                {{ txn.type === 'credit' ? '+' : '-' }}{{ txn.amount | currency: 'USD' : 'symbol' : '1.2-2' }}
              </ion-note>
            </ion-item>
          }
        </ion-list>
      </ion-card>

      <!-- Insights Teaser -->
      <ion-card class="insights-card">
        <ion-card-content>
          <div class="insights-row">
            <ion-icon name="trending-up-outline"></ion-icon>
            <div class="insights-text">
              <strong>Spending Insight</strong>
              <p>Your spending is 12% lower than last month. Keep it up!</p>
            </div>
          </div>
        </ion-card-content>
      </ion-card>

      <!-- Bottom spacer for tab bar -->
      <div style="height: 16px;"></div>
    </ion-content>
  `,
  styles: [
    `
      ion-header ion-toolbar {
        --background: #1a1a2e;
        --color: #ffffff;
      }

      .greeting {
        padding: 20px 16px 0;
      }

      .greeting-text {
        font-size: 0.9rem;
        color: var(--ion-color-medium);
        margin: 0 0 2px;
      }

      .greeting-name {
        font-size: 1.4rem;
        font-weight: 700;
        color: var(--ion-color-dark);
        margin: 0;
      }

      .transactions-card {
        margin: 0 16px 16px;
        border-radius: 12px;
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
      }

      .transactions-card ion-list {
        padding: 0;
      }

      .transactions-card ion-item {
        --padding-start: 12px;
        --padding-end: 12px;
        --inner-padding-end: 0;
      }

      .transactions-card ion-item h3 {
        font-size: 0.9rem;
        font-weight: 500;
      }

      .transactions-card ion-item p {
        font-size: 0.75rem;
      }

      .txn-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 36px;
        height: 36px;
        border-radius: 50%;
        background: #f4f5f8;
        margin-right: 8px;
      }

      .txn-icon ion-icon {
        font-size: 18px;
      }

      .insights-card {
        margin: 0 16px 16px;
        border-radius: 12px;
        --background: #eef2ff;
        box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
      }

      .insights-row {
        display: flex;
        align-items: flex-start;
        gap: 12px;
      }

      .insights-row ion-icon {
        font-size: 28px;
        color: var(--ion-color-secondary);
        flex-shrink: 0;
        margin-top: 2px;
      }

      .insights-text strong {
        display: block;
        font-size: 0.9rem;
        margin-bottom: 4px;
        color: var(--ion-color-dark);
      }

      .insights-text p {
        font-size: 0.8rem;
        color: var(--ion-color-medium);
        margin: 0;
        line-height: 1.4;
      }
    `,
  ],
})
export class HomeComponent {
  account = {
    id: 'acc-1001',
    name: 'Demo User',
    balance: 12450.0,
    type: 'Checking',
  };

  recentTransactions: Transaction[] = [
    {
      description: 'Salary Deposit',
      amount: 5200.0,
      date: 'Feb 15, 2026',
      type: 'credit',
    },
    {
      description: 'Grocery Store',
      amount: 87.45,
      date: 'Feb 14, 2026',
      type: 'debit',
    },
    {
      description: 'Electric Bill',
      amount: 142.0,
      date: 'Feb 12, 2026',
      type: 'debit',
    },
    {
      description: 'Freelance Payment',
      amount: 750.0,
      date: 'Feb 10, 2026',
      type: 'credit',
    },
    {
      description: 'Restaurant',
      amount: 62.3,
      date: 'Feb 9, 2026',
      type: 'debit',
    },
  ];

  get timeOfDay(): string {
    const hour = new Date().getHours();
    if (hour < 12) return 'morning';
    if (hour < 17) return 'afternoon';
    return 'evening';
  }

  constructor() {
    addIcons({
      walletOutline,
      sendOutline,
      cardOutline,
      qrCodeOutline,
      arrowUpOutline,
      arrowDownOutline,
      trendingUpOutline,
      shieldCheckmarkOutline,
    });
  }
}
