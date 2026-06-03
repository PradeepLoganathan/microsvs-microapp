import { Component, ElementRef, Input, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../environments/environment';

interface Goal {
  goalId: string;
  customerId: string;
  name: string;
  category: string;
  status: string;
  targetAmount: number;
  currentAmount: number;
  targetDate: string;
  progress: number;
  remaining: number;
}

interface GoalListView { goals: Goal[]; }

interface Transaction {
  id: string;
  date: string;
  merchant: string;
  amount: number;
  category: string;
  description: string;
  direction?: 'DEBIT' | 'CREDIT';
}

interface Statement {
  statementId: string;
  accountId: string;
  periodStart: string;
  periodEnd: string;
  totalDebits: number;
  totalCredits: number;
  transactions: Transaction[];
}

interface Nba {
  productId: string;
  headline: string;
  reason: string;
  cta: string;
}

interface NbaResponse { matched: boolean; offer: Nba | null; }

interface Application {
  applicationId: string;
  product: 'CASA' | 'TAKAFUL';
  productName: string;
  stage: string;
  statusLabel: string;
  accountNumber: string;
  summary: string;
  updatedAt: number;
}

interface Applications { applications: Application[]; }

interface StatementSummary {
  statementId: string;
  accountId: string;
  periodStart: string;
  periodEnd: string;
  totalDebits: number;
  transactionCount: number;
}

interface Balance { accountId: string; currentBalance: number; asOf: string; }

/** Cross-channel home-financing lead (e.g. applied via WhatsApp). */
interface LeadView { applied: boolean; status: string; amount: number; requestedAt: string; }

/** A unified card for the "Your accounts" list — primary + onboarded products. */
interface AccountCard {
  accountId: string;       // id used when switching the active account
  product: 'CASA' | 'TAKAFUL';
  productName: string;
  accountNumber: string;   // display label
  statusLabel: string;
  summary: string;
  balance: number | null;  // null until loaded / not applicable (Takaful)
  tappable: boolean;       // CASA accounts with an assigned number can be switched to
}

interface NewGoalForm {
  name: string;
  category: string;
  targetAmount: number | null;
  targetDate: string;
}

const TRAVEL_HINTS = ['air', 'airlines', 'airways', 'flight', 'hotel', 'agoda', 'booking', 'expedia'];

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="home">
      <header class="hero">
        <div class="greeting">Hi Sara,</div>
        <div class="subtitle">Here's what's happening this month</div>
        <div class="month-row" *ngIf="statement">
          <div class="month-card credits">
            <div class="month-label">In</div>
            <div class="month-amount">RM {{ statement.totalCredits | number:'1.2-2' }}</div>
          </div>
          <div class="month-card debits">
            <div class="month-label">Out</div>
            <div class="month-amount">RM {{ statement.totalDebits | number:'1.2-2' }}</div>
          </div>
        </div>
      </header>

      <section *ngIf="homeLead" class="lead-banner">
        <div class="lead-icon">🏠</div>
        <div class="lead-main">
          <div class="lead-title">Home Financing — application received</div>
          <div class="lead-sub">A banker will call you about MBSB Home Financing-i. Submitted via WhatsApp.</div>
        </div>
      </section>

      <section *ngIf="cards.length > 0" class="accounts">
        <div class="section-header">
          <h2>Your accounts</h2>
        </div>
        <div *ngFor="let c of cards" class="account-card"
             [class.account-takaful]="c.product === 'TAKAFUL'"
             [class.tappable]="c.tappable"
             [class.active]="isActive(c)"
             (click)="selectAccount(c)">
          <div class="account-icon">{{ c.product === 'CASA' ? '\uD83C\uDFE6' : '\uD83D\uDEE1\uFE0F' }}</div>
          <div class="account-main">
            <div class="account-name">{{ c.productName }}</div>
            <div class="account-meta">{{ c.accountNumber }}</div>
            <div class="account-balance" *ngIf="c.balance !== null">RM {{ c.balance | number:'1.2-2' }}</div>
            <div class="account-summary" *ngIf="c.balance === null">{{ c.summary }}</div>
          </div>
          <div class="account-right">
            <div class="account-status-pill" [class]="'pill-' + statusTier(c.statusLabel)">
              {{ c.statusLabel }}
            </div>
            <div class="account-active-tag" *ngIf="isActive(c)">Viewing</div>
          </div>
        </div>

        <button class="open-account" (click)="openOnboarding()">
          <span class="open-account-plus">＋</span> Open a new account
        </button>
      </section>

      <div #nbaSlot>
        <section *ngIf="nbaResponse?.matched && nbaResponse?.offer && !nbaDismissed"
                 class="nba-card" [class.flash]="nbaPulse">
          <div class="nba-pill">A moment for you</div>
          <div class="nba-headline">{{ nbaResponse?.offer?.headline }}</div>
          <div class="nba-reason">{{ nbaResponse?.offer?.reason }}</div>
          <div class="nba-actions">
            <button class="nba-cta">{{ nbaResponse?.offer?.cta }}</button>
            <button class="nba-dismiss" (click)="dismissNba()">Not now</button>
          </div>
        </section>

        <section *ngIf="nbaTapped && nbaResponse && !nbaResponse.matched && !nbaDismissed"
                 class="nba-card nba-empty" [class.flash]="nbaPulse">
          <div class="nba-pill nba-pill-none">No offer right now</div>
          <div class="nba-headline">Nothing pressing for this one.</div>
          <div class="nba-reason">We didn't see a match for this transaction — try a different one, or we'll surface offers when patterns change.</div>
          <div class="nba-actions">
            <button class="nba-dismiss" (click)="dismissNba()">Got it</button>
          </div>
        </section>
      </div>

      <section class="tabungs">
        <div class="section-header">
          <h2>Your Tabungs</h2>
          <button class="add-btn" (click)="toggleAdd()" [attr.aria-expanded]="showAdd">
            {{ showAdd ? 'Close' : '+ Add' }}
          </button>
        </div>

        <form *ngIf="showAdd" class="add-form" (ngSubmit)="createGoal()">
          <input class="form-input" placeholder="Goal name (e.g. Hajj 2028)"
                 [(ngModel)]="newGoal.name" name="name" required />
          <div class="form-row">
            <select class="form-input" [(ngModel)]="newGoal.category" name="category">
              <option value="HAJJ">Hajj</option>
              <option value="HOLIDAY">Holiday</option>
              <option value="HOUSE">House</option>
              <option value="EMERGENCY">Emergency</option>
              <option value="EDUCATION">Education</option>
              <option value="OTHER">Other</option>
            </select>
            <input class="form-input" type="number" min="1" placeholder="Target (RM)"
                   [(ngModel)]="newGoal.targetAmount" name="targetAmount" required />
            <input class="form-input" type="date"
                   [(ngModel)]="newGoal.targetDate" name="targetDate" required />
          </div>
          <button type="submit" class="submit-btn" [disabled]="creating">
            {{ creating ? 'Creating...' : 'Create Tabung' }}
          </button>
        </form>

        <div *ngIf="goals.length === 0 && !loadingGoals" class="empty">
          No tabungs yet — create one to start saving toward a goal.
        </div>

        <div *ngFor="let g of goals" class="tabung-card">
          <div class="tabung-head">
            <div class="tabung-icon">{{ categoryIcon(g.category) }}</div>
            <div class="tabung-main">
              <div class="tabung-name">{{ g.name }}</div>
              <div class="tabung-sub">by {{ g.targetDate }} · RM {{ g.remaining | number:'1.2-2' }} to go</div>
            </div>
            <button class="contribute-btn" (click)="quickContribute(g)"
                    [disabled]="g.status !== 'ACTIVE'">+RM 100</button>
          </div>
          <div class="progress-bar">
            <div class="progress-fill" [style.width.%]="g.progress * 100"
                 [class.completed]="g.status === 'COMPLETED'"></div>
          </div>
          <div class="tabung-foot">
            <span class="amounts">
              RM {{ g.currentAmount | number:'1.2-2' }} / RM {{ g.targetAmount | number:'1.2-2' }}
            </span>
            <span class="status" [class]="'status-' + g.status.toLowerCase()">{{ g.status }}</span>
          </div>
        </div>
      </section>

      <section *ngIf="largeTxns.length > 0" class="moments">
        <h2>Tap a transaction to see what your bank suggests</h2>
        <div *ngFor="let t of largeTxns" class="moment-row" (click)="fireNbaFor(t)">
          <div class="moment-merchant">{{ t.merchant }}</div>
          <div class="moment-meta">{{ t.date }} · {{ t.category }}</div>
          <div class="moment-amount">RM {{ t.amount | number:'1.2-2' }}</div>
        </div>
      </section>

      <div *ngIf="error" class="error">{{ error }}</div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }
    .home { max-width: 700px; margin: 0 auto; padding: 16px; }

    .hero { padding: 20px 18px; background: linear-gradient(135deg, #1a1a4e 0%, #4a1f7a 100%);
            border-radius: 16px; color: #fff; margin-bottom: 16px; }
    .greeting { font-size: 22px; font-weight: 600; }
    .subtitle { font-size: 13px; opacity: 0.85; margin-top: 2px; }
    .month-row { display: flex; gap: 10px; margin-top: 14px; }
    .month-card { flex: 1; background: rgba(255,255,255,0.12); border-radius: 10px; padding: 10px 12px; }
    .month-label { font-size: 11px; text-transform: uppercase; opacity: 0.8; letter-spacing: 0.6px; }
    .month-amount { font-size: 17px; font-weight: 600; margin-top: 2px; }
    .month-card.credits .month-amount { color: #b6f0c5; }
    .month-card.debits .month-amount { color: #ffd6c7; }

    .lead-banner { display:flex; gap:12px; align-items:center; background:#eef6ff;
                   border:1px solid #c9e0ff; border-left:4px solid #2456b5; border-radius:12px;
                   padding:12px 14px; margin-bottom:16px; }
    .lead-icon { font-size:22px; }
    .lead-main { min-width:0; }
    .lead-title { font-size:14px; font-weight:600; color:#1a1a2e; }
    .lead-sub { font-size:12px; color:#4a4a55; margin-top:2px; }

    .accounts { margin-bottom: 16px; }
    .account-card { display: grid; grid-template-columns: auto 1fr auto; column-gap: 12px;
                    background: #fff; border-radius: 12px; padding: 14px 16px; margin-bottom: 10px;
                    box-shadow: 0 2px 8px rgba(0,0,0,0.06); border-left: 4px solid #2456b5; }
    .account-card.account-takaful { border-left-color: #1f7a45; }
    .account-icon { width: 38px; height: 38px; border-radius: 10px; background: #eaf1ff;
                    display: flex; align-items: center; justify-content: center; font-size: 18px;
                    align-self: center; }
    .account-card.account-takaful .account-icon { background: #e6f6ec; }
    .account-main { min-width: 0; align-self: center; }
    .account-name { font-size: 15px; font-weight: 600; color: #1a1a2e; }
    .account-meta { font-size: 12px; color: #7e7e8a; font-variant-numeric: tabular-nums;
                    margin-top: 2px; letter-spacing: 0.3px; }
    .account-summary { font-size: 12px; color: #4a4a55; margin-top: 4px; }
    .account-status-pill { font-size: 10px; font-weight: 700; padding: 4px 9px; border-radius: 999px;
                           letter-spacing: 0.4px; align-self: center; white-space: nowrap; }
    .pill-active { background: #e6f6ec; color: #1f7a45; }
    .pill-pending { background: #fff4d6; color: #8a6700; }
    .pill-failed { background: #fdecea; color: #c0392b; }
    .account-card.tappable { cursor: pointer; transition: box-shadow 0.15s ease, border-color 0.15s ease; }
    .account-card.tappable:hover { box-shadow: 0 3px 12px rgba(0,0,0,0.10); }
    .account-card.active { border-left-color: #f1a800; background: #fffdf6; }
    .account-balance { font-size: 16px; font-weight: 700; color: #1a1a2e; margin-top: 4px;
                       font-variant-numeric: tabular-nums; }
    .account-right { display: flex; flex-direction: column; align-items: flex-end; gap: 6px;
                     align-self: center; }
    .account-active-tag { font-size: 10px; font-weight: 700; color: #8a6700; background: #fff4d6;
                          padding: 3px 8px; border-radius: 999px; letter-spacing: 0.4px; }
    .open-account { width: 100%; display: flex; align-items: center; justify-content: center; gap: 6px;
                    background: #f6f3fc; color: #4a1f7a; border: 1.5px dashed #c3b0e3; border-radius: 12px;
                    padding: 13px; font-size: 14px; font-weight: 600; cursor: pointer; font-family: inherit; }
    .open-account:hover { background: #efe8fa; }
    .open-account-plus { font-size: 16px; font-weight: 700; line-height: 1; }

    .nba-card { background: #fff8e6; border: 1px solid #f6d57b; border-left: 4px solid #f1a800;
                border-radius: 12px; padding: 16px 18px; margin-bottom: 16px;
                transition: transform 0.2s ease, box-shadow 0.2s ease; }
    .nba-card.flash { animation: nbaFlash 0.7s ease; }
    @keyframes nbaFlash {
      0%   { transform: scale(1);    box-shadow: 0 0 0 0 rgba(241,168,0,0.45); }
      40%  { transform: scale(1.02); box-shadow: 0 0 0 10px rgba(241,168,0,0.18); }
      100% { transform: scale(1);    box-shadow: 0 0 0 0 rgba(241,168,0,0); }
    }
    .nba-empty { background: #f5f5f8; border-color: #d6d6e0; border-left-color: #8c8c98; }
    .nba-empty .nba-headline { color: #4a4a55; }
    .nba-pill { display: inline-block; background: #f1a800; color: #fff; padding: 3px 10px;
                border-radius: 999px; font-size: 11px; font-weight: 600; letter-spacing: 0.4px; }
    .nba-pill-none { background: #8c8c98; }
    .nba-headline { font-size: 16px; font-weight: 600; color: #2c3e50; margin-top: 10px; }
    .nba-reason { font-size: 13px; color: #5e5e5e; margin-top: 6px; line-height: 1.45; }
    .nba-actions { display: flex; gap: 8px; margin-top: 12px; }
    .nba-cta { background: #1a1a4e; color: #fff; border: none; padding: 9px 16px;
               border-radius: 8px; font-size: 13px; font-weight: 500; cursor: pointer; }
    .nba-dismiss { background: transparent; color: #6e6e6e; border: 1px solid #d8d8d8;
                   padding: 9px 14px; border-radius: 8px; font-size: 13px; cursor: pointer; }

    .section-header { display: flex; justify-content: space-between; align-items: center;
                      margin: 4px 4px 12px; }
    .section-header h2 { font-size: 18px; font-weight: 600; color: #1a1a2e; }
    .add-btn { background: transparent; color: #4a1f7a; border: 1px solid #d3c3eb;
               border-radius: 8px; padding: 7px 14px; font-size: 13px; cursor: pointer; }

    .add-form { background: #fff; border-radius: 12px; padding: 14px; margin-bottom: 12px;
                box-shadow: 0 2px 8px rgba(0,0,0,0.06); display: flex; flex-direction: column; gap: 8px; }
    .form-input { padding: 9px 11px; border: 1px solid #d6d6df; border-radius: 8px; font-size: 13px;
                  font-family: inherit; }
    .form-row { display: grid; grid-template-columns: 1fr; gap: 8px; }
    .submit-btn { background: #4a1f7a; color: #fff; border: none; padding: 10px;
                  border-radius: 8px; font-size: 13px; font-weight: 500; cursor: pointer; }
    .submit-btn:disabled { background: #bdb1cf; }

    .tabung-card { background: #fff; border-radius: 12px; padding: 14px 16px; margin-bottom: 10px;
                   box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
    .tabung-head { display: flex; align-items: center; gap: 12px; }
    .tabung-icon { width: 38px; height: 38px; border-radius: 10px; background: #f0eaff;
                   display: flex; align-items: center; justify-content: center; font-size: 18px; }
    .tabung-main { flex: 1; min-width: 0; }
    .tabung-name { font-size: 15px; font-weight: 600; color: #1a1a2e; }
    .tabung-sub { font-size: 12px; color: #7e7e8a; margin-top: 1px; }
    .contribute-btn { background: #eef0f6; color: #4a1f7a; border: none; padding: 7px 11px;
                      border-radius: 8px; font-size: 12px; font-weight: 600; cursor: pointer; }
    .contribute-btn:disabled { background: #f4f5f8; color: #b9b9c2; cursor: not-allowed; }

    .progress-bar { height: 8px; background: #eef0f6; border-radius: 999px; overflow: hidden;
                    margin-top: 12px; }
    .progress-fill { height: 100%; background: #4a1f7a; border-radius: 999px; transition: width 0.4s ease; }
    .progress-fill.completed { background: #2eb872; }
    .tabung-foot { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; }
    .amounts { font-size: 12px; color: #5e5e6c; }
    .status { font-size: 10px; padding: 2px 8px; border-radius: 999px; font-weight: 600;
              letter-spacing: 0.4px; }
    .status-active { background: #e7f0ff; color: #2456b5; }
    .status-completed { background: #e6f6ec; color: #1f7a45; }
    .status-abandoned { background: #f4f4f6; color: #7e7e8a; }

    .moments { margin-top: 18px; }
    .moments h2 { font-size: 15px; font-weight: 500; color: #5e5e6c; margin: 4px 4px 10px; }
    .moment-row { display: grid; grid-template-columns: 1fr auto; row-gap: 2px;
                  background: #fff; border-radius: 10px; padding: 12px 14px; margin-bottom: 8px;
                  cursor: pointer; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
    .moment-row:hover { background: #faf8ff; }
    .moment-merchant { font-size: 14px; font-weight: 500; color: #1a1a2e; }
    .moment-meta { font-size: 11px; color: #8c8c98; grid-column: 1; }
    .moment-amount { font-size: 14px; font-weight: 600; color: #4a1f7a; grid-row: 1 / span 2;
                     align-self: center; }

    .empty { padding: 18px; text-align: center; color: #95a5a6; font-size: 13px;
             background: #fff; border-radius: 12px; }
    .error { padding: 12px; color: #c0392b; background: #fdecea; border-radius: 8px;
             font-size: 13px; margin-top: 12px; }
  `]
})
export class AppComponent implements OnInit {
  // Account context, pushed in by the shell as `account-id` / `customer-id`
  // attributes. accountId drives the data tabs (In/Out, NBA) and switches when
  // the user taps an account; customerId is the constant logged-in customer.
  private _accountId = environment.accountId;
  private _customerId = environment.accountId; // demo default: same id
  private ready = false;

  @Input() set accountId(value: string) {
    if (!value || value === this._accountId) return;
    this._accountId = value;
    if (this.ready) this.loadActiveAccountData();
  }

  @Input() set customerId(value: string) {
    if (!value || value === this._customerId) return;
    this._customerId = value;
    if (this.ready) { this.loadAccounts(); this.loadGoals(); this.loadLead(); }
  }

  @ViewChild('nbaSlot', { static: false }) nbaSlot?: ElementRef<HTMLElement>;

  goals: Goal[] = [];
  accounts: Application[] = [];
  cards: AccountCard[] = [];
  homeLead: LeadView | null = null;
  statement: Statement | null = null;
  largeTxns: Transaction[] = [];
  nbaResponse: NbaResponse | null = null;
  nbaDismissed = false;
  nbaTapped = false; // true once the user has explicitly tapped a moment row
  nbaPulse = false;  // toggled to retrigger the flash animation

  loadingGoals = false;
  creating = false;
  error: string | null = null;
  showAdd = false;
  newGoal: NewGoalForm = { name: '', category: 'HAJJ', targetAmount: null, targetDate: '' };

  constructor(private http: HttpClient, private host: ElementRef<HTMLElement>) {}

  ngOnInit(): void {
    this.ready = true;
    this.loadGoals();
    this.loadAccounts();
    this.loadActiveAccountData();
    this.loadLead();
    this.applyTabungQueryParams();
  }

  /** Cross-channel: a home-financing lead raised on another channel (WhatsApp). */
  private loadLead(): void {
    this.http.get<LeadView>(
      `${environment.advisorApiUrl}/advisor/${this._customerId}/home-financing`)
      .subscribe({
        next: (lead) => { this.homeLead = lead && lead.applied ? lead : null; },
        error: () => { this.homeLead = null; },
      });
  }

  /** Reload everything tied to the currently-active account. */
  private loadActiveAccountData(): void {
    this.loadStatement();
  }

  /**
   * Honor a `?openTabung=CATEGORY&name=…&target=…&date=…` URL hint so the advisor
   * can pre-fill and open the "+ Add Tabung" form when a customer accepts its
   * proposed action.
   */
  private applyTabungQueryParams(): void {
    const params = new URLSearchParams(window.location.search);
    const category = params.get('openTabung');
    if (!category) return;
    const target = Number(params.get('target'));
    this.newGoal = {
      name: params.get('name') || '',
      category: category.toUpperCase(),
      targetAmount: Number.isFinite(target) && target > 0 ? target : null,
      targetDate: params.get('date') || '',
    };
    this.showAdd = true;
  }

  private loadAccounts(): void {
    this.http.get<Applications>(
      `${environment.onboardingApiUrl}/onboarding/customers/${this._customerId}/applications`)
      .subscribe({
        next: (data) => { this.accounts = data.applications || []; this.buildCards(); },
        error: (err) => { console.warn('Failed to load accounts', err); this.buildCards(); },
      });
  }

  /** Build the unified account list: the customer's primary CASA + onboarded products. */
  private buildCards(): void {
    const primary: AccountCard = {
      accountId: this._customerId,
      product: 'CASA',
      productName: 'MBSB CASA-i',
      accountNumber: '••' + this.tail(this._customerId),
      statusLabel: 'Active',
      summary: 'Your everyday account',
      balance: null,
      tappable: true,
    };

    const fromApps: AccountCard[] = this.accounts.map((a) => ({
      accountId: a.accountNumber,            // CASA accountId / Takaful policyNumber
      product: a.product,
      productName: a.productName,
      accountNumber: a.accountNumber || 'Pending',
      statusLabel: a.statusLabel,
      summary: a.summary,
      balance: null,
      tappable: a.product === 'CASA' && !!a.accountNumber,
    }));

    this.cards = [primary, ...fromApps];
    this.loadBalances();
  }

  /** Fetch the current balance for each switchable (CASA) account card. */
  private loadBalances(): void {
    this.cards
      .filter((c) => c.tappable && c.accountId)
      .forEach((c) => {
        this.http.get<Balance>(
          `${environment.statementApiUrl}/accounts/${c.accountId}/balance`)
          .subscribe({
            next: (b) => { c.balance = b.currentBalance; },
            error: () => { /* leave balance null */ },
          });
      });
  }

  private tail(id: string): string {
    return id.length >= 4 ? id.slice(-4) : id;
  }

  /** True when this card is the account currently driving the data tabs. */
  isActive(card: AccountCard): boolean {
    return card.accountId === this._accountId;
  }

  /** Ask the shell to open the onboarding flow (CASA/Takaful chooser). */
  openOnboarding(): void {
    this.host.nativeElement.dispatchEvent(new CustomEvent('mbsb-navigate', {
      detail: { type: 'ONBOARD' },
      bubbles: true,
      composed: true,
    }));
  }

  /** Tapping a switchable account asks the shell to make it the active account. */
  selectAccount(card: AccountCard): void {
    if (!card.tappable) return;
    this.host.nativeElement.dispatchEvent(new CustomEvent('mbsb-select-account', {
      detail: {
        accountId: card.accountId,
        customerId: this._customerId,
        productName: card.productName,
        accountNumber: card.accountNumber,
      },
      bubbles: true,
      composed: true,
    }));
  }

  statusTier(label: string): 'active' | 'pending' | 'failed' {
    if (label === 'Ready to use' || label === 'Active') return 'active';
    if (label === 'Application failed') return 'failed';
    return 'pending';
  }

  private loadGoals(): void {
    this.loadingGoals = true;
    this.http.get<GoalListView>(
      `${environment.goalsApiUrl}/customers/${this._customerId}/goals/active`)
      .subscribe({
        next: (data) => { this.goals = data.goals || []; this.loadingGoals = false; },
        error: (err) => {
          this.loadingGoals = false;
          console.warn('Failed to load goals', err);
        },
      });
  }

  private loadStatement(): void {
    // Reset active-account-scoped state before (re)loading.
    this.statement = null;
    this.largeTxns = [];
    this.nbaResponse = null;
    this.nbaTapped = false;
    this.nbaDismissed = false;
    this.error = null;

    const acct = this._accountId;
    // No hardcoded statement id — find the account's latest statement, then load it.
    this.http.get<StatementSummary[]>(
      `${environment.statementApiUrl}/accounts/${acct}/statements`)
      .subscribe({
        next: (summaries) => {
          if (acct !== this._accountId) return; // a newer switch superseded this
          if (!summaries || summaries.length === 0) { this.statement = null; return; }
          // Ignore empty statements (e.g. the opening statement) so In/Out reflects
          // real activity. Prefer the curated demo statement (keeps the December
          // travel NBA story for the primary account); else the latest with data.
          const withTxns = summaries.filter((s) => s.transactionCount > 0);
          const pool = withTxns.length ? withTxns : summaries;
          const preferred = pool.find((s) => s.statementId === environment.statementId);
          const latest = pool.slice()
            .sort((a, b) => b.periodEnd.localeCompare(a.periodEnd))[0];
          this.loadStatementDetail(acct, (preferred ?? latest).statementId);
        },
        error: (err) => {
          this.error = 'Could not load statement.';
          console.error(err);
        },
      });
  }

  private loadStatementDetail(acct: string, statementId: string): void {
    this.http.get<Statement>(
      `${environment.statementApiUrl}/accounts/${acct}/statements/${statementId}`)
      .subscribe({
        next: (stmt) => {
          if (acct !== this._accountId) return;
          this.statement = stmt;
          // NBA "moments" are about spending — only consider debits (skip salary/transfers).
          const txns = (stmt.transactions || []).filter((t) => t.direction !== 'CREDIT');
          this.largeTxns = txns
            .slice()
            .sort((a, b) => b.amount - a.amount)
            .slice(0, 3);

          // Auto-fire NBA on the largest debit, if any (silent on load — no scroll/flash)
          if (this.largeTxns.length > 0) {
            this.fireNbaFor(this.largeTxns[0], false);
          }
        },
        error: (err) => {
          this.error = 'Could not load statement.';
          console.error(err);
        },
      });
  }

  fireNbaFor(t: Transaction, fromUserTap = true): void {
    this.nbaDismissed = false;
    if (fromUserTap) this.nbaTapped = true;
    const overseas = this.looksOverseas(t);
    const body = {
      trigger: 'LARGE_TRANSACTION_VIEWED',
      transactionId: t.id,
      amount: t.amount,
      merchant: t.merchant,
      category: t.category,
      overseas: overseas,
    };
    this.http.post<NbaResponse>(
      `${environment.recommendationApiUrl}/accounts/${this._accountId}/nba/evaluate`, body)
      .subscribe({
        next: (resp) => {
          this.nbaResponse = resp;
          if (fromUserTap) {
            this.scrollNbaIntoView();
            this.flashCard();
          }
        },
        error: (err) => console.warn('NBA call failed', err),
      });
  }

  private scrollNbaIntoView(): void {
    setTimeout(() => {
      this.nbaSlot?.nativeElement?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, 60);
  }

  private flashCard(): void {
    this.nbaPulse = false;
    setTimeout(() => { this.nbaPulse = true; }, 20);
    setTimeout(() => { this.nbaPulse = false; }, 800);
  }

  dismissNba(): void {
    this.nbaDismissed = true;
  }

  quickContribute(g: Goal): void {
    this.http.post<Goal>(
      `${environment.goalsApiUrl}/goals/${g.goalId}/contribute`, { amount: 100 })
      .subscribe({
        next: (updated) => {
          const idx = this.goals.findIndex(x => x.goalId === g.goalId);
          if (idx >= 0) this.goals[idx] = updated;
        },
        error: (err) => console.warn('Contribute failed', err),
      });
  }

  toggleAdd(): void {
    this.showAdd = !this.showAdd;
    if (this.showAdd) {
      const yearAhead = new Date();
      yearAhead.setFullYear(yearAhead.getFullYear() + 2);
      this.newGoal = {
        name: '',
        category: 'HAJJ',
        targetAmount: null,
        targetDate: yearAhead.toISOString().slice(0, 10),
      };
    }
  }

  createGoal(): void {
    if (!this.newGoal.name || !this.newGoal.targetAmount || !this.newGoal.targetDate) return;
    this.creating = true;
    const body = {
      name: this.newGoal.name,
      category: this.newGoal.category,
      targetAmount: this.newGoal.targetAmount,
      targetDate: this.newGoal.targetDate,
    };
    this.http.post<Goal>(
      `${environment.goalsApiUrl}/customers/${this._customerId}/goals`, body)
      .subscribe({
        next: (created) => {
          this.goals = [created, ...this.goals];
          this.creating = false;
          this.showAdd = false;
        },
        error: (err) => {
          this.creating = false;
          this.error = 'Could not create tabung.';
          console.error(err);
        },
      });
  }

  categoryIcon(c: string): string {
    switch (c) {
      case 'HAJJ': return '\uD83D\uDD4B';
      case 'HOLIDAY': return '\u2708\uFE0F';
      case 'HOUSE': return '\uD83C\uDFE0';
      case 'EMERGENCY': return '\uD83D\uDEE1\uFE0F';
      case 'EDUCATION': return '\uD83C\uDF93';
      default: return '\u2B50';
    }
  }

  private looksOverseas(t: Transaction): boolean {
    const m = (t.merchant || '').toLowerCase();
    // Demo heuristic: hotel/airlines names tagged in seed are domestic-looking,
    // so default false. Real client would flag based on FX or geo.
    return m.includes('overseas') || m.includes('tokyo') || m.includes('london');
  }
}
