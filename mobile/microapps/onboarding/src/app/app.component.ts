import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../environments/environment';

interface Status {
  applicationId: string;
  customerId: string;
  stage: string;
  // CASA
  accountId?: string | null;
  welcomeMessage?: string | null;
  // Takaful
  selectedPlanId?: string | null;
  policyNumber?: string | null;
  effectiveDate?: string | null;
  certificateSummary?: string | null;
  failureReason?: string | null;
}

interface Plan {
  id: string;
  name: string;
  description: string;
  minMonthlyContribution: number;
}

type Flow = 'casa' | 'takaful';
const STORAGE_KEY = 'mbsb-onboarding';
const CUSTOMER_ID = 'acc-1001';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="onb">
      <div class="topbar">
        <div class="title">{{ flow === 'takaful' ? 'Takaful Sign-up' : flow === 'casa' ? 'Open an Account' : 'Get Started' }}</div>
        <button *ngIf="flow" class="reset" (click)="reset()">Start over</button>
      </div>

      <div class="body">
        <!-- choose a journey -->
        <div *ngIf="!flow" class="choose">
          <p class="lead">What would you like to do?</p>
          <button class="choice" (click)="startFlow('casa')">
            <span class="ic">🏦</span>
            <span><b>Open a CASA account</b><small>Current / savings account</small></span>
          </button>
          <button class="choice" (click)="startFlow('takaful')">
            <span class="ic">🛡️</span>
            <span><b>Sign up for Takaful</b><small>Shariah-compliant protection</small></span>
          </button>
        </div>

        <!-- processing (auto stages) -->
        <div *ngIf="flow && busy && !isStable(stage)" class="processing">
          <div class="spinner"></div>
          <p>{{ prettyStage(stage) }}…</p>
        </div>

        <ng-container *ngIf="flow && status && isStable(stage)">
          <div class="stepbar" *ngIf="stage?.startsWith('AWAITING')">Step {{ stepNumber() }} of 2</div>

          <!-- CASA: details -->
          <form *ngIf="stage === 'AWAITING_DETAILS'" class="form" (ngSubmit)="submitCasaDetails()">
            <h3>Your details</h3>
            <input [(ngModel)]="d.fullName" name="fullName" placeholder="Full name" />
            <input [(ngModel)]="d.email" name="email" placeholder="Email" />
            <input [(ngModel)]="d.mobile" name="mobile" placeholder="Mobile" />
            <select [(ngModel)]="d.accountType" name="accountType">
              <option value="SAVINGS">Savings account</option>
              <option value="CURRENT">Current account</option>
            </select>
            <button class="primary" type="submit" [disabled]="busy">Continue</button>
          </form>

          <!-- CASA: eKYC (the resume point) -->
          <form *ngIf="stage === 'AWAITING_EKYC_CONSENT'" class="form" (ngSubmit)="submitCasaEkyc()">
            <div class="resumed">✓ Step 1 saved — you've resumed here.</div>
            <h3>Identity verification (eKYC)</h3>
            <select [(ngModel)]="e.idType" name="idType">
              <option value="NRIC">NRIC</option>
              <option value="PASSPORT">Passport</option>
            </select>
            <input [(ngModel)]="e.idNumber" name="idNumber" placeholder="ID number" />
            <label class="check"><input type="checkbox" [(ngModel)]="e.consentGiven" name="consent" /> I consent to identity verification</label>
            <button class="primary" type="submit" [disabled]="busy">Verify &amp; open account</button>
          </form>

          <!-- CASA: done -->
          <div *ngIf="flow === 'casa' && stage === 'COMPLETED'" class="done">
            <div class="tick">✓</div>
            <h3>Account opened</h3>
            <p>{{ status.welcomeMessage }}</p>
            <div class="pill">Account: {{ status.accountId }}</div>
          </div>

          <!-- TAKAFUL: choose plan -->
          <div *ngIf="stage === 'AWAITING_PLAN_SELECTION'" class="form">
            <h3>Choose a Takaful plan</h3>
            <button *ngFor="let p of plans" class="plan" (click)="submitPlan(p.id)" [disabled]="busy">
              <b>{{ p.name }}</b>
              <small>{{ p.description }}</small>
              <span class="min">from RM {{ p.minMonthlyContribution }}/mo</span>
            </button>
          </div>

          <!-- TAKAFUL: contribution (resume point) -->
          <form *ngIf="stage === 'AWAITING_CONTRIBUTION'" class="form" (ngSubmit)="submitContribution()">
            <div class="resumed">✓ Plan selected — you've resumed here.</div>
            <h3>Your contribution</h3>
            <input [(ngModel)]="c.amount" name="amount" type="number" placeholder="Amount (RM)" />
            <select [(ngModel)]="c.frequency" name="frequency">
              <option value="MONTHLY">Monthly</option>
              <option value="ANNUAL">Annual</option>
            </select>
            <input [(ngModel)]="c.beneficiaryName" name="beneficiary" placeholder="Beneficiary name" />
            <button class="primary" type="submit" [disabled]="busy">Activate policy</button>
          </form>

          <!-- TAKAFUL: done -->
          <div *ngIf="flow === 'takaful' && stage === 'COMPLETED'" class="done">
            <div class="tick">✓</div>
            <h3>You're covered</h3>
            <p>{{ status.certificateSummary }}</p>
            <div class="pill">Policy: {{ status.policyNumber }}</div>
          </div>

          <!-- failure -->
          <div *ngIf="stage === 'FAILED'" class="failed">
            <h3>We couldn't complete this</h3>
            <p>{{ status.failureReason }}</p>
            <button class="primary" (click)="reset()">Start over</button>
          </div>
        </ng-container>
      </div>
    </div>
  `,
  styles: [`
    :host { display:block; height:100%; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif; }
    .onb { display:flex; flex-direction:column; height:100%; background:#f4f6f9; }
    .topbar { display:flex; align-items:center; justify-content:space-between; padding:14px 16px; background:#1a1a2e; color:#fff; }
    .title { font-size:15px; font-weight:600; }
    .reset { background:none; border:1px solid rgba(255,255,255,.3); color:#fff; border-radius:999px; padding:5px 12px; font-size:12px; cursor:pointer; }
    .body { flex:1; overflow-y:auto; padding:18px 16px; }
    .lead { color:#555; margin:0 0 14px; }

    .choice { width:100%; display:flex; align-items:center; gap:14px; text-align:left; background:#fff; border:1px solid #e6e8ef; border-radius:14px; padding:16px; margin-bottom:12px; cursor:pointer; }
    .choice:hover { border-color:#0f3460; }
    .choice .ic { font-size:28px; }
    .choice b { display:block; color:#1a1a2e; font-size:15px; }
    .choice small { color:#7f8c8d; font-size:12.5px; }

    .stepbar { font-size:12px; color:#8a90a6; text-transform:uppercase; letter-spacing:.5px; margin-bottom:10px; }
    .form { display:flex; flex-direction:column; gap:10px; }
    .form h3 { margin:0 0 4px; color:#1a1a2e; font-size:17px; }
    .form input, .form select { border:1px solid #d8dce8; border-radius:10px; padding:12px 14px; font-size:14px; outline:none; }
    .form input:focus, .form select:focus { border-color:#0f3460; }
    .check { display:flex; align-items:center; gap:8px; font-size:13px; color:#555; }
    .check input { width:auto; }
    .primary { background:#0f3460; color:#fff; border:none; border-radius:10px; padding:13px; font-size:15px; font-weight:600; cursor:pointer; margin-top:4px; }
    .primary:disabled { background:#cdd2e0; cursor:not-allowed; }
    .resumed { background:#eafaf1; color:#16a06a; border:1px solid #bde8d2; border-radius:10px; padding:9px 12px; font-size:12.5px; }

    .plan { text-align:left; background:#fff; border:1px solid #e6e8ef; border-radius:12px; padding:14px; cursor:pointer; display:flex; flex-direction:column; gap:3px; }
    .plan:hover { border-color:#0f3460; }
    .plan b { color:#1a1a2e; font-size:15px; }
    .plan small { color:#7f8c8d; font-size:12.5px; }
    .plan .min { color:#0f3460; font-size:12px; font-weight:600; margin-top:4px; }

    .processing { text-align:center; padding:48px 16px; color:#555; }
    .spinner { width:34px; height:34px; border:3px solid #d8dce8; border-top-color:#0f3460; border-radius:50%; margin:0 auto 14px; animation:spin .8s linear infinite; }
    @keyframes spin { to { transform:rotate(360deg); } }

    .done, .failed { text-align:center; padding:32px 16px; }
    .done .tick { width:56px; height:56px; border-radius:50%; background:#16c784; color:#fff; font-size:30px; display:flex; align-items:center; justify-content:center; margin:0 auto 14px; }
    .done h3, .failed h3 { color:#1a1a2e; margin:0 0 8px; }
    .done p, .failed p { color:#555; font-size:14px; line-height:1.5; }
    .pill { display:inline-block; margin-top:14px; background:#1a1a2e; color:#fff; border-radius:999px; padding:8px 16px; font-size:13px; font-weight:600; }
    .failed h3 { color:#c0392b; }
  `]
})
export class AppComponent implements OnInit {
  flow: Flow | null = null;
  appId: string | null = null;
  stage: string | null = null;
  status: Status | null = null;
  plans: Plan[] = [];
  busy = false;

  d = { fullName: '', email: '', mobile: '', accountType: 'SAVINGS' };
  e = { idType: 'NRIC', idNumber: '', consentGiven: false };
  c: { amount: number | null; frequency: string; beneficiaryName: string } = { amount: null, frequency: 'MONTHLY', beneficiaryName: '' };

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    // Resume an in-progress application if one was persisted (the "reopen" demo).
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      try {
        const { flow, appId } = JSON.parse(saved);
        if (flow && appId) {
          this.flow = flow;
          this.appId = appId;
          if (flow === 'takaful') this.loadPlans();
          this.poll();
        }
      } catch {
        localStorage.removeItem(STORAGE_KEY);
      }
    }
  }

  startFlow(flow: Flow): void {
    this.flow = flow;
    this.appId = `${flow}-${crypto.randomUUID()}`;
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ flow, appId: this.appId }));
    if (flow === 'takaful') this.loadPlans();
    this.busy = true;
    this.http.post(this.url('start'), { customerId: CUSTOMER_ID }).subscribe({
      next: () => this.poll(),
      error: () => (this.busy = false),
    });
  }

  submitCasaDetails(): void {
    this.submit({ details: this.d });
  }

  submitCasaEkyc(): void {
    this.submit({ ekyc: this.e });
  }

  submitPlan(planId: string): void {
    this.submit({ planId });
  }

  submitContribution(): void {
    this.submit({ contribution: this.c });
  }

  reset(): void {
    localStorage.removeItem(STORAGE_KEY);
    this.flow = null;
    this.appId = null;
    this.stage = null;
    this.status = null;
    this.busy = false;
  }

  isStable(stage: string | null): boolean {
    return !!stage && (stage.startsWith('AWAITING_') || stage === 'COMPLETED' || stage === 'FAILED');
  }

  stepNumber(): number {
    return this.stage === 'AWAITING_EKYC_CONSENT' || this.stage === 'AWAITING_CONTRIBUTION' ? 2 : 1;
  }

  prettyStage(stage: string | null): string {
    switch (stage) {
      case 'RUNNING_EKYC': return 'Verifying your identity';
      case 'CREATING_ACCOUNT': return 'Opening your account';
      case 'CHECKING_ELIGIBILITY': return 'Checking eligibility';
      case 'ACTIVATING_POLICY': return 'Activating your policy';
      case 'ISSUING_CERTIFICATE': return 'Issuing your certificate';
      default: return 'Working';
    }
  }

  private submit(body: any): void {
    this.busy = true;
    this.http.post(this.url('submit'), body).subscribe({
      next: () => this.poll(),
      error: () => (this.busy = false),
    });
  }

  private poll(): void {
    this.busy = true;
    this.http.get<Status>(this.url('status')).subscribe({
      next: (s) => {
        this.stage = s.stage;
        this.status = s;
        if (this.isStable(s.stage)) {
          this.busy = false;
        } else {
          setTimeout(() => this.poll(), 800); // keep polling through auto stages
        }
      },
      error: () => (this.busy = false),
    });
  }

  private loadPlans(): void {
    this.http.get<Plan[]>(`${environment.apiBaseUrl}/onboarding/takaful/plans`).subscribe({
      next: (p) => (this.plans = p),
      error: () => {},
    });
  }

  private url(action: string): string {
    return `${environment.apiBaseUrl}/onboarding/${this.flow}/${this.appId}/${action}`;
  }
}
