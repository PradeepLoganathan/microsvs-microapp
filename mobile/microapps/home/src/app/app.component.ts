import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
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
  private readonly accountId = environment.accountId;
  private readonly customerId = environment.accountId; // demo: same id
  private readonly statementId = environment.statementId;

  @ViewChild('nbaSlot', { static: false }) nbaSlot?: ElementRef<HTMLElement>;

  goals: Goal[] = [];
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

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.loadGoals();
    this.loadStatement();
  }

  private loadGoals(): void {
    this.loadingGoals = true;
    this.http.get<GoalListView>(
      `${environment.goalsApiUrl}/customers/${this.customerId}/goals/active`)
      .subscribe({
        next: (data) => { this.goals = data.goals || []; this.loadingGoals = false; },
        error: (err) => {
          this.loadingGoals = false;
          console.warn('Failed to load goals', err);
        },
      });
  }

  private loadStatement(): void {
    this.http.get<Statement>(
      `${environment.statementApiUrl}/accounts/${this.accountId}/statements/${this.statementId}`)
      .subscribe({
        next: (stmt) => {
          this.statement = stmt;
          const txns = stmt.transactions || [];
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
      `${environment.recommendationApiUrl}/accounts/${this.accountId}/nba/evaluate`, body)
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
      `${environment.goalsApiUrl}/customers/${this.customerId}/goals`, body)
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
