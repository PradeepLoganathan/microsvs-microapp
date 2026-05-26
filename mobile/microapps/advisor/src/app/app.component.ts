import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../environments/environment';

interface Proposal {
  actionType: string;
  title: string;
  description: string;
  targetAmount: number;
  monthlyContribution: number;
  productId: string;
  rationale: string;
}

interface AskResponse {
  reply: string;
  proposal: Proposal | null;
  humanHandoffOffered: boolean;
}

interface ChatMessage {
  role: 'user' | 'advisor';
  text: string;
  proposal?: Proposal | null;
  handoff?: boolean;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="advisor">
      <div class="topbar">
        <div class="avatar">K</div>
        <div>
          <div class="name">K — Wealth Advisor</div>
          <div class="status">Shariah-aware · grounded in your accounts</div>
        </div>
      </div>

      <div class="messages" #scroll>
        <div *ngFor="let m of messages" class="row" [class.user]="m.role === 'user'">
          <div class="bubble" [class.user]="m.role === 'user'">{{ m.text }}</div>

          <div *ngIf="m.proposal && m.proposal.actionType !== 'NONE'" class="proposal">
            <div class="p-badge">Proposed action</div>
            <div class="p-title">{{ m.proposal.title }}</div>
            <div class="p-desc">{{ m.proposal.description }}</div>
            <div class="p-figs" *ngIf="m.proposal.targetAmount > 0">
              <div class="fig">
                <span class="lbl">Target</span><span class="val">RM {{ fmt(m.proposal.targetAmount) }}</span>
              </div>
              <div class="fig">
                <span class="lbl">Monthly</span><span class="val">RM {{ fmt(m.proposal.monthlyContribution) }}</span>
              </div>
            </div>
            <div class="p-rationale" *ngIf="m.proposal.rationale">{{ m.proposal.rationale }}</div>
          </div>

          <div *ngIf="m.handoff" class="handoff">
            🤝 A licensed advisor can set this up — <button class="link" (click)="ask('Yes, please connect me with an advisor.')">talk to a human</button>
          </div>
        </div>

        <div *ngIf="loading" class="row">
          <div class="bubble typing"><span></span><span></span><span></span></div>
        </div>
      </div>

      <div class="suggestions" *ngIf="messages.length <= 1 && !loading">
        <button *ngFor="let s of suggestions" (click)="ask(s)">{{ s }}</button>
      </div>

      <div class="composer">
        <input
          [(ngModel)]="input"
          (keyup.enter)="send()"
          [disabled]="loading"
          placeholder="Ask K about your money…"
        />
        <button class="send" (click)="send()" [disabled]="loading || !input.trim()">Send</button>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; height: 100%; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
    .advisor { display: flex; flex-direction: column; height: 100%; background: #f4f6f9; }

    .topbar { display: flex; align-items: center; gap: 12px; padding: 14px 16px; background: #1a1a2e; color: #fff; }
    .avatar { width: 38px; height: 38px; border-radius: 50%; background: linear-gradient(135deg,#0f3460,#e94560); display: flex; align-items: center; justify-content: center; font-weight: 700; }
    .name { font-size: 15px; font-weight: 600; }
    .status { font-size: 11px; opacity: .7; }

    .messages { flex: 1; overflow-y: auto; padding: 16px; display: flex; flex-direction: column; gap: 12px; }
    .row { display: flex; flex-direction: column; align-items: flex-start; max-width: 100%; }
    .row.user { align-items: flex-end; }

    .bubble { max-width: 82%; padding: 11px 14px; border-radius: 16px; font-size: 14px; line-height: 1.45; background: #fff; color: #222; border-bottom-left-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,.08); white-space: pre-wrap; }
    .bubble.user { background: #0f3460; color: #fff; border-radius: 16px; border-bottom-right-radius: 4px; }

    .proposal { margin-top: 8px; max-width: 88%; background: #fff; border: 1px solid #e6e8ef; border-left: 4px solid #e94560; border-radius: 12px; padding: 14px; box-shadow: 0 2px 10px rgba(0,0,0,.06); }
    .p-badge { font-size: 10px; text-transform: uppercase; letter-spacing: .6px; color: #e94560; font-weight: 700; margin-bottom: 6px; }
    .p-title { font-size: 15px; font-weight: 700; color: #1a1a2e; }
    .p-desc { font-size: 13px; color: #555; margin-top: 2px; line-height: 1.45; }
    .p-figs { display: flex; gap: 10px; margin-top: 12px; }
    .fig { flex: 1; background: #f7f8fc; border-radius: 10px; padding: 8px 10px; display: flex; flex-direction: column; }
    .lbl { font-size: 10px; text-transform: uppercase; letter-spacing: .5px; color: #8a90a6; }
    .val { font-size: 15px; font-weight: 700; color: #0f3460; margin-top: 2px; }
    .p-rationale { font-size: 12px; color: #7f8c8d; margin-top: 10px; font-style: italic; }

    .handoff { margin-top: 8px; font-size: 12.5px; color: #555; background: #fff; border: 1px dashed #cdd2e0; border-radius: 10px; padding: 8px 12px; }
    .link { background: none; border: none; color: #e94560; font-weight: 600; cursor: pointer; padding: 0; font-size: 12.5px; }

    .typing { display: inline-flex; gap: 4px; align-items: center; }
    .typing span { width: 7px; height: 7px; border-radius: 50%; background: #b6bccf; animation: blink 1.2s infinite both; }
    .typing span:nth-child(2) { animation-delay: .2s; }
    .typing span:nth-child(3) { animation-delay: .4s; }
    @keyframes blink { 0%,80%,100% { opacity: .3; } 40% { opacity: 1; } }

    .suggestions { display: flex; flex-wrap: wrap; gap: 8px; padding: 0 16px 8px; }
    .suggestions button { background: #fff; border: 1px solid #d8dce8; color: #0f3460; border-radius: 999px; padding: 8px 13px; font-size: 12.5px; cursor: pointer; }
    .suggestions button:hover { border-color: #0f3460; }

    .composer { display: flex; gap: 8px; padding: 12px 16px; background: #fff; border-top: 1px solid #e6e8ef; }
    .composer input { flex: 1; border: 1px solid #d8dce8; border-radius: 999px; padding: 11px 16px; font-size: 14px; outline: none; }
    .composer input:focus { border-color: #0f3460; }
    .send { background: #e94560; color: #fff; border: none; border-radius: 999px; padding: 0 20px; font-size: 14px; font-weight: 600; cursor: pointer; }
    .send:disabled { background: #cdd2e0; cursor: not-allowed; }
  `]
})
export class AppComponent {
  private readonly customerId = 'acc-1001';
  private readonly apiUrl = `${environment.apiBaseUrl}/advisor/${this.customerId}/ask`;

  messages: ChatMessage[] = [
    {
      role: 'advisor',
      text: "As-salamu alaykum 👋 I'm K, your wealth advisor. Ask me about saving, budgeting, or planning a goal — I'll use your real account data.",
    },
  ];
  input = '';
  loading = false;
  suggestions = [
    'Can I afford to save for Hajj in 2 years?',
    "How's my spending looking?",
    'Help me start a savings goal',
  ];

  constructor(private http: HttpClient) {}

  ask(text: string): void {
    this.input = text;
    this.send();
  }

  send(): void {
    const message = this.input.trim();
    if (!message || this.loading) return;

    this.messages.push({ role: 'user', text: message });
    this.input = '';
    this.loading = true;

    this.http.post<AskResponse>(this.apiUrl, { message }).subscribe({
      next: (res) => {
        this.messages.push({
          role: 'advisor',
          text: res.reply,
          proposal: res.proposal,
          handoff: res.humanHandoffOffered,
        });
        this.loading = false;
        this.scrollSoon();
      },
      error: () => {
        this.messages.push({
          role: 'advisor',
          text: "Sorry, I couldn't reach the advisor service just now. Please try again.",
        });
        this.loading = false;
        this.scrollSoon();
      },
    });
    this.scrollSoon();
  }

  private scrollSoon(): void {
    setTimeout(() => {
      const el = document.querySelector('mf-advisor')?.shadowRoot?.querySelector('.messages')
        ?? document.querySelector('.messages');
      if (el) (el as HTMLElement).scrollTop = (el as HTMLElement).scrollHeight;
    }, 50);
  }

  fmt(n: number): string {
    return (n ?? 0).toLocaleString(undefined, { maximumFractionDigits: 0 });
  }
}
