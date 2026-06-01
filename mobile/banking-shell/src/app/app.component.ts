import { Component, OnInit } from '@angular/core';
import { IonApp, IonRouterOutlet } from '@ionic/angular/standalone';
import { PersonaService } from './services/persona.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [IonApp, IonRouterOutlet],
  template: `
    <ion-app>
      <ion-router-outlet></ion-router-outlet>
    </ion-app>
  `,
})
export class AppComponent implements OnInit {
  constructor(private personaService: PersonaService) {}

  ngOnInit(): void {
    // Make the active persona available to CSS via [data-persona] on <body>
    // so per-persona themes can be added without touching component code.
    const persona = this.personaService.current();
    document.body.setAttribute('data-persona', persona ?? 'default');
  }
}
