import { createApplication } from '@angular/platform-browser';
import { createCustomElement } from '@angular/elements';
import { ApplicationRef } from '@angular/core';
import { AppComponent } from './app/app.component';
import { appConfig } from './app/app.config';

(async () => {
  const appRef: ApplicationRef = await createApplication(appConfig);

  const advisorElement = createCustomElement(AppComponent, {
    injector: appRef.injector,
  });

  customElements.define('mf-advisor', advisorElement);
})();
