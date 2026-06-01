import { Component, ElementRef, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../environments/environment';

interface Offer {
  code: string;
  title: string;
  description: string;
  starterProductId: string;
  bonusAmount: number;
}
interface Customer {
  customerId: string;
  stage: string;
  email: string | null;
  phone: string | null;
  channel: string | null;
  offer: Offer | null;
  accountId: string | null;
  kycRef: string | null;
}
interface Tile { id: string; title: string; subtitle: string; icon: string; }
interface PreLogin { tiles: Tile[]; customerCount: number; }

const STORAGE_KEY = 'mbsb-customer-id';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="pl">
      <div class="hero">
        <div class="brand">MBSB <span>NextGen</span></div>
        <div class="tag">Be Bold. Bank Smart.</div>
        <div class="proof" *ngIf="info">{{ info.customerCount }}+ customers have joined</div>
      </div>

      <div class="body">
        <!-- VISITOR: tiles + register -->
        <ng-container *ngIf="stage === 'VISITOR'">
          <div class="tiles" *ngIf="info">
            <div class="tile" *ngFor="let t of info.tiles">
              <span class="ic">{{ t.icon }}</span>
              <div><b>{{ t.title }}</b><small>{{ t.subtitle }}</small></div>
            </div>
          </div>
          <form class="card form" (ngSubmit)="register()">
            <h3>Create your account</h3>
            <input [(ngModel)]="reg.email" name="email" placeholder="Email" />
            <input [(ngModel)]="reg.phone" name="phone" placeholder="Mobile" />
            <select [(ngModel)]="reg.channel" name="channel">
              <option value="MOBILE">Joining on mobile</option>
              <option value="REFERRAL">Referred by a friend</option>
              <option value="WEB">Found you on the web</option>
            </select>
            <button class="primary" type="submit" [disabled]="busy">Register</button>
          </form>
        </ng-container>

        <!-- REGISTERED: welcome offer + eKYC -->
        <ng-container *ngIf="stage === 'REGISTERED'">
          <div class="card offer" *ngIf="customer?.offer as o">
            <div class="badge">🎁 Welcome offer</div>
            <div class="o-title">{{ o.title }}</div>
            <div class="o-desc">{{ o.description }}</div>
            <div class="o-bonus" *ngIf="o.bonusAmount > 0">RM {{ o.bonusAmount }} bonus</div>
          </div>
          <form class="card form" (ngSubmit)="completeKyc()">
            <h3>Verify your identity (eKYC)</h3>
            <select [(ngModel)]="ekyc.idType" name="idType">
              <option value="NRIC">NRIC</option>
              <option value="PASSPORT">Passport</option>
            </select>
            <input [(ngModel)]="ekyc.idNumber" name="idNumber" placeholder="ID number" />
            <label class="check"><input type="checkbox" [(ngModel)]="ekyc.consent" name="consent" /> I consent to identity verification</label>
            <button class="primary" type="submit" [disabled]="busy">Verify &amp; finish</button>
          </form>
        </ng-container>

        <!-- CUSTOMER: done -->
        <div class="card done" *ngIf="stage === 'CUSTOMER'">
          <div class="tick">✓</div>
          <h3>You're all set!</h3>
          <p>Your account is ready. Welcome to MBSB NextGen.</p>
          <button class="primary" (click)="enterApp()">Enter your account →</button>
        </div>

        <div class="footer" *ngIf="stage === 'VISITOR'">
          <button class="link" (click)="startOver()">Reset</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host { display:block; height:100%; font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif; }
    .pl { display:flex; flex-direction:column; height:100%; background:#f4f6f9; }
    .hero { background:linear-gradient(135deg,#1a1a2e,#0f3460); color:#fff; padding:28px 20px 22px; }
    .brand { font-size:22px; font-weight:700; } .brand span { color:#e94560; }
    .tag { font-size:13px; opacity:.85; margin-top:4px; }
    .proof { font-size:12px; opacity:.7; margin-top:10px; }
    .body { flex:1; overflow-y:auto; padding:16px; display:flex; flex-direction:column; gap:14px; }

    .tiles { display:flex; flex-direction:column; gap:10px; }
    .tile { display:flex; align-items:center; gap:12px; background:#fff; border:1px solid #e6e8ef; border-radius:12px; padding:13px 15px; }
    .tile .ic { font-size:24px; } .tile b { display:block; color:#1a1a2e; font-size:14px; } .tile small { color:#7f8c8d; font-size:12px; }

    .card { background:#fff; border:1px solid #e6e8ef; border-radius:14px; padding:16px; box-shadow:0 2px 10px rgba(0,0,0,.05); }
    .form { display:flex; flex-direction:column; gap:10px; }
    .form h3 { margin:0 0 4px; color:#1a1a2e; font-size:17px; }
    .form input, .form select { border:1px solid #d8dce8; border-radius:10px; padding:12px 14px; font-size:14px; outline:none; background:#fff; color:#1a1a2e; color-scheme:light; }
    .form input:focus, .form select:focus { border-color:#0f3460; }
    .check { display:flex; align-items:center; gap:8px; font-size:13px; color:#555; } .check input { width:auto; }
    .primary { background:#0f3460; color:#fff; border:none; border-radius:10px; padding:13px; font-size:15px; font-weight:600; cursor:pointer; }
    .primary:disabled { background:#cdd2e0; cursor:not-allowed; }

    .offer { border-left:4px solid #e94560; }
    .badge { font-size:11px; text-transform:uppercase; letter-spacing:.6px; color:#e94560; font-weight:700; }
    .o-title { font-size:16px; font-weight:700; color:#1a1a2e; margin-top:6px; }
    .o-desc { font-size:13px; color:#555; margin-top:3px; line-height:1.45; }
    .o-bonus { display:inline-block; margin-top:10px; background:#1a1a2e; color:#fff; border-radius:999px; padding:6px 14px; font-size:13px; font-weight:600; }

    .done { text-align:center; }
    .done .tick { width:56px; height:56px; border-radius:50%; background:#16c784; color:#fff; font-size:30px; display:flex; align-items:center; justify-content:center; margin:0 auto 12px; }
    .done h3 { color:#1a1a2e; margin:0 0 6px; } .done p { color:#555; font-size:14px; margin-bottom:16px; }

    .footer { text-align:center; }
    .link { background:none; border:none; color:#8a90a6; font-size:12.5px; cursor:pointer; }
  `]
})
export class AppComponent implements OnInit {
  private readonly base = environment.apiBaseUrl;
  customerId = '';
  stage: string | null = null;
  customer: Customer | null = null;
  info: PreLogin | null = null;
  busy = false;

  reg = { email: '', phone: '', channel: 'MOBILE' };
  ekyc = { idType: 'NRIC', idNumber: '', consent: false };

  constructor(private http: HttpClient, private el: ElementRef) {}

  ngOnInit(): void {
    this.customerId = localStorage.getItem(STORAGE_KEY) ?? this.mint();
    // createVisitor is idempotent and re-creates state if the (in-memory) journal was wiped.
    this.http.post(`${this.base}/customers/${this.customerId}/visitor`, { channel: 'MOBILE' }).subscribe({
      next: () => this.refresh(),
      error: () => this.refresh(),
    });
    this.http.get<PreLogin>(`${this.base}/customers/prelogin`).subscribe({ next: (p) => (this.info = p), error: () => {} });
  }

  register(): void {
    this.busy = true;
    this.http.post(`${this.base}/customers/${this.customerId}/register`, this.reg).subscribe({
      next: () => this.refresh(),
      error: () => (this.busy = false),
    });
  }

  completeKyc(): void {
    this.busy = true;
    this.http.post(`${this.base}/customers/${this.customerId}/kyc`, this.ekyc).subscribe({
      next: () => this.refresh(),
      error: () => (this.busy = false),
    });
  }

  enterApp(): void {
    this.el.nativeElement.dispatchEvent(new CustomEvent('enter-app', { bubbles: true, composed: true }));
  }

  startOver(): void {
    this.customerId = this.mint();
    this.stage = null;
    this.customer = null;
    this.http.post(`${this.base}/customers/${this.customerId}/visitor`, { channel: 'MOBILE' })
      .subscribe({ next: () => this.refresh(), error: () => this.refresh() });
  }

  private refresh(): void {
    this.http.get<Customer>(`${this.base}/customers/${this.customerId}`).subscribe({
      next: (c) => { this.customer = c; this.stage = c.stage; this.busy = false; },
      error: () => (this.busy = false),
    });
  }

  private mint(): string {
    const id = 'visitor-' + crypto.randomUUID();
    localStorage.setItem(STORAGE_KEY, id);
    return id;
  }
}
