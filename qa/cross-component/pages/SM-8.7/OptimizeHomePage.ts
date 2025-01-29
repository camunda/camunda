import {Page, Locator, expect} from '@playwright/test';

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
    this.modalCloseButton = page.getByText('Close', {exact: true});
    this.optimizeBanner = page.getByRole('link', {
      name: 'Camunda logo Optimize',
    });
  }

  async clickCollectionsLink(): Promise<void> {
    try {
      await this.modalCloseButton.click({timeout: 60000});
      await this.collectionsLink.click({timeout: 60000});
    } catch (error) {
      await this.collectionsLink.click({timeout: 60000});
    }
  }

  async clickDashboardLink(): Promise<void> {
    try {
      await this.modalCloseButton.click({timeout: 60000});
      await this.dashboardLink.click({timeout: 60000});
    } catch (error) {
      await this.dashboardLink.click({timeout: 60000});
    }
  }

  async assertProcessHasBeenImported(processId: string): Promise<void> {
    await this.clickDashboardLink();
    await this.page.reload();
    await expect(this.page.getByRole('link', {name: processId})).toBeVisible({
      timeout: 180000,
    });
  }
}
export {OptimizeHomePage};
