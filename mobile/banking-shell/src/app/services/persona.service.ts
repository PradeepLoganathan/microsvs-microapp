import { Injectable } from '@angular/core';
import { environment } from '../../environments/environment';

const STORAGE_KEY = 'mbsb-persona';
const URL_PARAM = 'persona';

/**
 * Resolves the active customer persona for this session and maps it to the
 * micro-frontend manifest channel that should drive the UI.
 *
 * Resolution order: URL query param (?persona=mass-affluent) → localStorage →
 * default (no persona, baseline manifest channel).
 *
 * Setting a persona persists to localStorage so it survives navigation. To
 * switch personas at runtime the page must reload — custom elements can't be
 * re-registered, so the bundle has to be re-fetched against the new manifest.
 */
@Injectable({ providedIn: 'root' })
export class PersonaService {
  private resolved: string | null;

  constructor() {
    this.resolved = this.resolveAndPersist();
  }

  /** "digital-native", "mass-affluent", or null when no persona is active. */
  current(): string | null {
    return this.resolved;
  }

  /** Manifest channel for the active persona — `demo-<persona>` or the default. */
  channel(): string {
    if (this.resolved) return `${environment.manifestChannel}-${this.resolved}`;
    return environment.manifestChannel;
  }

  /** Persists a persona and reloads so the new manifest takes effect. */
  set(persona: string | null): void {
    try {
      if (persona) localStorage.setItem(STORAGE_KEY, persona);
      else localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* localStorage may be unavailable in private mode — ignore */
    }
    this.resolved = persona;
    window.location.reload();
  }

  private resolveAndPersist(): string | null {
    try {
      const params = new URLSearchParams(window.location.search);
      const fromUrl = params.get(URL_PARAM);
      if (fromUrl) {
        try { localStorage.setItem(STORAGE_KEY, fromUrl); } catch { /* noop */ }
        return fromUrl;
      }
      return localStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }
}
