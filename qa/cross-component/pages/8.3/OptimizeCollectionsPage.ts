import {Page, Locator} from '@playwright/test';

class OptimizeCollectionsPage {
  private page: Page;
  readonly collectionsPageHeader: Locator;
  readonly createNewButton: Locator;
  readonly reportOption: Locator;
  readonly modalCloseButton: Locator;
  readonly processReport: Locator;
  readonly collectionsLink: Locator;

  constructor(page: Page) {
    this.page = page;
    this.collectionsPageHeader = page.getByRole('heading', {
      name: 'Collections, Dashboards, and Reports',
    });
    this.createNewButton = page.getByRole('button', {
      name: 'Create New',
      exact: true,
    });
    this.reportOption = page.getByText('Report', {exact: true});
    this.modalCloseButton = page.getByText('Close', {exact: true});
    this.processReport = page.getByText('Process Report').first();
    this.collectionsLink = page.getByRole('link', {name: 'Collections'});
  }

  async clickCreateNewButton(): Promise<void> {
    try {
      await this.modalCloseButton.click({timeout: 60000});
      await this.collectionsLink.click({timeout: 90000});
      await this.createNewButton.click({timeout: 90000});
    } catch (error) {
      await this.collectionsLink.click({timeout: 90000});
      await this.createNewButton.click({timeout: 90000});
    }
  }

  async clickReportOption(): Promise<void> {
    await this.reportOption.click();
  }

  async clickMostRecentProcessReport(): Promise<void> {
    await this.processReport.click();
  }
}
export {OptimizeCollectionsPage};
