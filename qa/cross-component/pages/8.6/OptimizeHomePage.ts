import {Page, Locator} from '@playwright/test';

class OptimizeHomePage {
  private page: Page;
  readonly collectionsLink: Locator;
  readonly dashboardLink: Locator;
  readonly modalCloseButton: Locator;
  readonly optimizeBanner: Locator;

  constructor(page: Page) {
    this.page = page;
    this.collectionsLink = page.getByRole('link', {name: 'Collections'});
    this.dashboardLink = page.getByRole('link', {name: 'Dashboard'});
    this.modalCloseButton = page.locator('button').filter({hasText: /^Close$/});
    this.optimizeBanner = page.getByRole('link', {
      name: 'Camunda logo Optimize',
    });
  }

  async clickCollectionsLink(): Promise<void> {
    if (await this.modalCloseButton.isVisible({timeout: 60000})) {
      await this.modalCloseButton.click();
    }
    await this.collectionsLink.click({timeout: 60000});
  }

  async clickDashboardLink(): Promise<void> {
    if (await this.modalCloseButton.isVisible({timeout: 60000})) {
      this.modalCloseButton.click();
    }
    await this.dashboardLink.click();
  }
}
export {OptimizeHomePage};
