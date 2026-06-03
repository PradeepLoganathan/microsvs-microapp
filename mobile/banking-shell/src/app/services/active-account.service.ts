import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

/** The account currently driving the data tabs (statements / analysis / home in-out). */
export interface ActiveAccount {
  accountId: string;
  /** The logged-in customer — constant across accounts (one customer owns many accounts). */
  customerId: string;
  productName: string;
  accountNumber?: string;
}

const STORAGE_KEY = 'mbsb-active-account';

/** The customer's existing primary CASA account — owns the seeded demo statements. */
const PRIMARY: ActiveAccount = {
  accountId: 'acc-1001',
  customerId: 'acc-1001',
  productName: 'MBSB CASA-i',
};

/**
 * Tracks which account the data micro-apps (statements, analysis, home in-out)
 * should render. Defaults to the customer's primary account; tapping an account
 * on the home screen switches it. Persisted so it survives navigation/reload.
 */
@Injectable({ providedIn: 'root' })
export class ActiveAccountService {
  private readonly subject: BehaviorSubject<ActiveAccount>;
  /** Replays the current account, then emits on every switch. */
  readonly account$: Observable<ActiveAccount>;

  constructor() {
    this.subject = new BehaviorSubject<ActiveAccount>(this.load());
    this.account$ = this.subject.asObservable();
  }

  current(): ActiveAccount {
    return this.subject.value;
  }

  setActive(account: ActiveAccount): void {
    const next: ActiveAccount = {
      ...account,
      customerId: account.customerId || this.subject.value.customerId,
    };
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
    } catch {
      /* localStorage may be unavailable in private mode — ignore */
    }
    this.subject.next(next);
  }

  /** Switch back to the customer's primary account. */
  reset(): void {
    this.setActive(PRIMARY);
  }

  private load(): ActiveAccount {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) return JSON.parse(raw) as ActiveAccount;
    } catch {
      /* noop */
    }
    return PRIMARY;
  }
}
