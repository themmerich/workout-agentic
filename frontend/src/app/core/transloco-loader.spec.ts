import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Translation } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';

import { TranslocoHttpLoader } from './transloco-loader';

describe('TranslocoHttpLoader', () => {
  let loader: TranslocoHttpLoader;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
    loader = TestBed.inject(TranslocoHttpLoader);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('merges the root translation file and every part into one translation', async () => {
    const translation = firstValueFrom(loader.getTranslation('de'));

    httpTesting.expectOne('/i18n/de.json').flush({ common: { save: 'Speichern' } });
    httpTesting.expectOne('/i18n/core/de.json').flush({ shell: { app: 'App' }, login: { submit: 'Anmelden' } });
    httpTesting.expectOne('/i18n/admin/de.json').flush({ users: { title: 'Benutzer' } });
    httpTesting.expectOne('/i18n/habits/de.json').flush({ habits: { title: 'Gewohnheiten' } });
    httpTesting.expectOne('/i18n/tenants/de.json').flush({ tenants: { title: 'Mandanten' } });

    expect(await translation).toEqual<Translation>({
      shell: { app: 'App' },
      login: { submit: 'Anmelden' },
      common: { save: 'Speichern' },
      users: { title: 'Benutzer' },
      habits: { title: 'Gewohnheiten' },
      tenants: { title: 'Mandanten' },
    });
  });
});
