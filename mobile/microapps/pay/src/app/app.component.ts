import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../environments/environment';

interface Application {
  applicationId: string;
  product: 'CASA' | 'TAKAFUL';
  productName: string;
  accountNumber: string;
}
interface Applications { applications: Application[]; }

interface Beneficiary {
  id: string;
  name: string;
  bank: string;
  accountNumber: string;
}

interface OwnAccount { accountId: string; label: string; }

interface TransferResult {
  ok: boolean;
  transferId: string;
  fromAccountId: string;
  to: string;
  amount: number;
  message: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="pay">
      <!-- Success -->
      <section *ngIf="result" class="done-card">
        <div class="tick">✓</div>
        <div class="done-title">Transfer successful</div>
        <div class="done-amount">RM {{ result.amount | number:'1.2-2' }}</div>
        <div class="done-sub">sent to {{ result.to }}</div>
        <div class="done-ref">Ref: {{ result.transferId }}</div>
        <button class="primary-btn" (click)="reset()">Make another transfer</button>
      </section>

      <!-- Form -->
      <form *ngIf="!result" class="form-card" (ngSubmit)="submit()">
        <h2>Transfer money</h2>

        <label class="field">
          <span class="label">From</span>
          <select class="input" [(ngModel)]="fromAccountId" name="from">
            <option *ngFor="let a of ownAccounts" [value]="a.accountId">{{ a.label }}</option>
          </select>
        </label>

        <label class="field">
          <span class="label">To</span>
          <select class="input" [(ngModel)]="toSelection" name="to">
            <option value="" disabled>Select destination</option>
            <optgroup label="My accounts" *ngIf="destOwnAccounts().length">
              <option *ngFor="let a of destOwnAccounts()" [value]="'own:' + a.accountId">{{ a.label }}</option>
            </optgroup>
            <optgroup label="Beneficiaries" *ngIf="beneficiaries.length">
              <option *ngFor="let b of beneficiaries" [value]="'ben:' + b.id">{{ b.name }} · {{ b.bank }}</option>
            </optgroup>
          </select>
        </label>

        <label class="field">
          <span class="label">Amount (RM)</span>
          <input class="input" type="number" min="1" step="0.01" placeholder="0.00"
                 [(ngModel)]="amount" name="amount" />
        </label>

        <label class="field">
          <span class="label">Note (optional)</span>
          <input class="input" type="text" maxlength="60" placeholder="e.g. Rent, Dinner"
                 [(ngModel)]="note" name="note" />
        </label>

        <div *ngIf="error" class="error">{{ error }}</div>

        <button type="submit" class="primary-btn" [disabled]="submitting">
          {{ submitting ? 'Sending…' : 'Transfer' }}
        </button>
      </form>
    </div>
  `,
  styles: [`
    :host { display:block; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
    .pay { max-width:700px; margin:0 auto; padding:16px; }

    .form-card, .done-card { background:#fff; border-radius:14px; padding:20px 18px;
                             box-shadow:0 2px 10px rgba(0,0,0,0.06); }
    h2 { font-size:18px; font-weight:600; color:#1a1a2e; margin:0 0 16px; }

    .field { display:block; margin-bottom:14px; }
    .label { display:block; font-size:12px; font-weight:600; color:#6a6a78;
             text-transform:uppercase; letter-spacing:0.4px; margin-bottom:6px; }
    .input { width:100%; box-sizing:border-box; padding:11px 12px; border:1px solid #d6d6df;
             border-radius:10px; font-size:14px; font-family:inherit; background:#fff; color:#1a1a2e; }
    .input:focus { outline:none; border-color:#4a1f7a; }

    .primary-btn { width:100%; background:#4a1f7a; color:#fff; border:none; padding:13px;
                   border-radius:10px; font-size:15px; font-weight:600; cursor:pointer; margin-top:4px; }
    .primary-btn:disabled { background:#bdb1cf; cursor:not-allowed; }

    .error { background:#fdecea; color:#c0392b; border-radius:8px; padding:10px 12px;
             font-size:13px; margin-bottom:12px; }

    .done-card { text-align:center; }
    .tick { width:56px; height:56px; margin:6px auto 12px; border-radius:50%; background:#e6f6ec;
            color:#1f7a45; font-size:30px; line-height:56px; }
    .done-title { font-size:17px; font-weight:600; color:#1a1a2e; }
    .done-amount { font-size:26px; font-weight:700; color:#1a1a2e; margin-top:8px;
                   font-variant-numeric:tabular-nums; }
    .done-sub { font-size:13px; color:#5e5e6c; margin-top:4px; }
    .done-ref { font-size:11px; color:#9a9aa6; margin:8px 0 18px; letter-spacing:0.3px; }
  `]
})
export class AppComponent implements OnInit {
  private _accountId = 'acc-1001';
  private _customerId = 'acc-1001';
  private ready = false;

  @Input() set accountId(value: string) {
    if (!value) return;
    this._accountId = value;
    this.fromAccountId = value; // default the source to the active account
  }

  @Input() set customerId(value: string) {
    if (!value || value === this._customerId) return;
    this._customerId = value;
    if (this.ready) this.loadAccounts();
  }

  ownAccounts: OwnAccount[] = [];
  beneficiaries: Beneficiary[] = [];

  fromAccountId = 'acc-1001';
  toSelection = '';          // 'own:<accountId>' or 'ben:<id>'
  amount: number | null = null;
  note = '';

  submitting = false;
  result: TransferResult | null = null;
  error: string | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.ready = true;
    this.fromAccountId = this._accountId;
    this.loadAccounts();
    this.loadBeneficiaries();
  }

  private loadAccounts(): void {
    const primary: OwnAccount = {
      accountId: this._customerId,
      label: `MBSB CASA-i · ${this._customerId}`,
    };
    this.http.get<Applications>(
      `${environment.onboardingApiUrl}/onboarding/customers/${this._customerId}/applications`)
      .subscribe({
        next: (data) => {
          const apps = (data.applications || [])
            .filter((a) => a.product === 'CASA' && !!a.accountNumber)
            .map((a) => ({ accountId: a.accountNumber, label: `${a.productName} · ${a.accountNumber}` }));
          this.ownAccounts = [primary, ...apps];
        },
        error: () => { this.ownAccounts = [primary]; },
      });
  }

  private loadBeneficiaries(): void {
    this.http.get<Beneficiary[]>(`${environment.paymentApiUrl}/payments/beneficiaries`)
      .subscribe({
        next: (b) => { this.beneficiaries = b || []; },
        error: () => { /* leave empty */ },
      });
  }

  destOwnAccounts(): OwnAccount[] {
    return this.ownAccounts.filter((a) => a.accountId !== this.fromAccountId);
  }

  submit(): void {
    this.error = null;
    this.result = null;
    if (!this.fromAccountId) { this.error = 'Choose a source account.'; return; }
    if (!this.toSelection) { this.error = 'Choose a destination.'; return; }
    if (!this.amount || this.amount <= 0) { this.error = 'Enter a valid amount.'; return; }
    if (this.toSelection === 'own:' + this.fromAccountId) {
      this.error = 'Source and destination must differ.'; return;
    }

    const body: Record<string, unknown> = {
      fromAccountId: this.fromAccountId,
      amount: this.amount,
      note: this.note,
    };
    if (this.toSelection.startsWith('own:')) body['toAccountId'] = this.toSelection.slice(4);
    else body['beneficiaryId'] = this.toSelection.slice(4);

    this.submitting = true;
    this.http.post<TransferResult>(`${environment.paymentApiUrl}/payments/transfer`, body)
      .subscribe({
        next: (r) => {
          this.submitting = false;
          if (r.ok) this.result = r;
          else this.error = r.message || 'Transfer failed.';
        },
        error: () => {
          this.submitting = false;
          this.error = 'Transfer failed. Please try again.';
        },
      });
  }

  reset(): void {
    this.result = null;
    this.toSelection = '';
    this.amount = null;
    this.note = '';
    this.error = null;
  }
}
