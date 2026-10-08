import { Component } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';

/**
 * The page a tenant's user lands on after signing in. A placeholder for now: the app's own
 * features grow from here, each as a domain of its own next to this one.
 */
@Component({
  selector: 'app-start-page',
  imports: [TranslocoDirective],
  templateUrl: './start-page.html',
})
export class StartPage {}
