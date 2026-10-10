import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Translation, TranslocoLoader } from '@jsverse/transloco';
import { forkJoin, map } from 'rxjs';

/**
 * The translation files next to the root one: `core` (shell, login, profile)
 * and one per domain. Each file holds its own top-level namespaces; they never
 * overlap with each other or with the root file's `common` vocabulary.
 */
const TRANSLATION_PARTS = ['core', 'admin', 'habits', 'tenants'];

/**
 * Loads the root translation file `public/i18n/<lang>.json` (the `common`
 * vocabulary: generic button labels and the like) plus every part from
 * `public/i18n/<part>/<lang>.json`, and merges them into one translation. The
 * `public/` folder is served at the app root (see angular.json assets), so the
 * files resolve at `/i18n/...`.
 */
@Injectable({ providedIn: 'root' })
export class TranslocoHttpLoader implements TranslocoLoader {
  private readonly http = inject(HttpClient);

  getTranslation(lang: string) {
    const urls = [`/i18n/${lang}.json`, ...TRANSLATION_PARTS.map((part) => `/i18n/${part}/${lang}.json`)];
    return forkJoin(urls.map((url) => this.http.get<Translation>(url))).pipe(
      map((translations) => Object.assign({}, ...translations) as Translation),
    );
  }
}
