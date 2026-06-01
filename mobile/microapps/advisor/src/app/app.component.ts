import { Component, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../environments/environment';

interface ActionView {
  type: 'CASA' | 'TAKAFUL' | 'TABUNG' | 'ADVISOR_HUMAN';
  label: string;
  params: Record<string, string>;
}

interface AskResponse {
  message: string;
  action: ActionView | null;
  needsHuman: boolean;
}

interface ChatMessage {
  role: 'user' | 'advisor';
  text: string;
  action?: ActionView | null;
  needsHuman?: boolean;
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
          <div class="status">Be Bold. Bank Smart.</div>
        </div>
      </div>

      <div class="messages" #scroll>
        <div *ngFor="let m of messages" class="row" [class.user]="m.role === 'user'">
          <div class="bubble" [class.user]="m.role === 'user'">{{ m.text }}</div>

          <button
            *ngIf="m.action"
            class="action"
            [class.action-handoff]="m.action.type === 'ADVISOR_HUMAN'"
            (click)="onActionClick(m.action)"
          >
            {{ actionIcon(m.action.type) }} {{ m.action.label }}
            <span class="action-chev">›</span>
          </button>

          <div *ngIf="m.needsHuman && !m.action" class="handoff">
            🤝 A licensed advisor will follow up about this.
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

    .action {
      margin-top: 8px;
      display: inline-flex; align-items: center; gap: 8px;
      background: #fff; border: 1px solid #d8dce8; border-left: 4px solid #e94560;
      color: #1a1a2e; font-size: 13.5px; font-weight: 600;
      padding: 10px 14px; border-radius: 12px;
      cursor: pointer; transition: transform .12s ease, box-shadow .12s ease, border-color .12s ease;
      box-shadow: 0 2px 8px rgba(0,0,0,.06);
      align-self: flex-start;
    }
    .action:hover { transform: translateY(-1px); box-shadow: 0 4px 14px rgba(0,0,0,.08); border-color: #0f3460; }
    .action:active { transform: translateY(0); }
    .action-chev { margin-left: auto; color: #8a90a6; font-size: 18px; line-height: 1; }
    .action-handoff { border-left-color: #f1a800; }

    .handoff { margin-top: 8px; font-size: 12.5px; color: #555; background: #fff; border: 1px dashed #cdd2e0; border-radius: 10px; padding: 8px 12px; align-self: flex-start; }

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
      text: "Hi 👋 I'm K, your wealth advisor. Ask me about saving, budgeting, or planning a goal — I'll use your real account data.",
    },
  ];
  input = '';
  loading = false;
  suggestions = [
    'Can I afford to save for a holiday in 2 years?',
    "How's my spending looking?",
    'Help me start an emergency fund',
  ];

  constructor(private http: HttpClient, private elementRef: ElementRef) {}

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
          text: res.message,
          action: res.action,
          needsHuman: res.needsHuman,
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

  /**
   * Translate the agent's structured action into a custom event that bubbles
   * out of the Shadow DOM. The shell listens for `mbsb-navigate` and decides
   * which tab to navigate to + which params to pre-fill. The micro-app owns
   * the action label; the shell owns the navigation surface — clean separation.
   */
  onActionClick(action: ActionView): void {
    const detail = { type: action.type, params: action.params || {} };
    this.elementRef.nativeElement.dispatchEvent(
      new CustomEvent('mbsb-navigate', { detail, bubbles: true, composed: true })
    );
  }

  actionIcon(type: ActionView['type']): string {
    switch (type) {
      case 'TABUNG': return '⭐';
      case 'CASA': return '🏦';
      case 'TAKAFUL': return '🛡️';
      case 'ADVISOR_HUMAN': return '🤝';
      default: return '→';
    }
  }

  private scrollSoon(): void {
    setTimeout(() => {
      const el = document.querySelector('mf-advisor')?.shadowRoot?.querySelector('.messages')
        ?? document.querySelector('.messages');
      if (el) (el as HTMLElement).scrollTop = (el as HTMLElement).scrollHeight;
    }, 50);
  }
}
