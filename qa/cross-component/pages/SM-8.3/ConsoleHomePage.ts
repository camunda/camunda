import {Page, Locator} from '@playwright/test';

class ConsoleHomePage {
  private page: Page;
  readonly consoleBanner: Locator;

  constructor(page: Page) {
    this.page = page;
    this.consoleBanner = page.getByRole('link', {
      name: 'Camunda logo Console',
    });
  }
}

export {ConsoleHomePage};
