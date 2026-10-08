import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';

import { StartPage } from './start-page';

const translations = {
  home: {
    title: 'Welcome',
    intro: 'This is where the app begins.',
  },
};

describe('StartPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        StartPage,
        TranslocoTestingModule.forRoot({
          langs: { en: translations },
          translocoConfig: { availableLangs: ['en'], defaultLang: 'en' },
          preloadLangs: true,
        }),
      ],
      providers: [provideZonelessChangeDetection()],
    }).compileComponents();
  });

  it('welcomes the signed-in user', () => {
    const fixture = TestBed.createComponent(StartPage);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('h1')?.textContent).toContain('Welcome');
    expect(element.textContent).toContain('This is where the app begins.');
  });
});
