import {Page, Locator, expect} from '@playwright/test';

class ConnectorMarketplacePage {
  private page: Page;
  readonly searchForConnectorTextbox: Locator;
  readonly downloadToProjectButton: Locator;
  readonly snackbar: Locator;
  readonly closeButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.searchForConnectorTextbox = page.getByPlaceholder(
      'Search for a connector',
    );
    this.downloadToProjectButton = page
      .getByRole('button', {name: 'Download to project'})
      .first();
    this.snackbar = page.locator('[data-test="snackbar"]');
    this.closeButton = page.getByRole('button', {name: 'Close'});
  }

  async clickSearchForConnectorTextbox(): Promise<void> {
    await this.searchForConnectorTextbox.click({timeout: 60000});
  }

  async fillSearchForConnectorTextbox(connectorName: string): Promise<void> {
    await this.searchForConnectorTextbox.fill(connectorName, {timeout: 60000});
  }

  async clickDownloadToProjectButton(): Promise<void> {
    await this.downloadToProjectButton.click({timeout: 60000});
  }

  async clickSnackbar(): Promise<void> {
    await this.snackbar.click({timeout: 120000});
  }

  async clickCloseButton(): Promise<void> {
    await this.closeButton.click({timeout: 60000});
  }

  async downloadConnectorToProject(): Promise<void> {
    try {
      await this.clickDownloadToProjectButton();
      await expect(this.snackbar).toBeVisible({
        timeout: 90000,
      });
      await this.clickSnackbar();
    } catch {
      await this.clickCloseButton();
    }
  }
}

export {ConnectorMarketplacePage};
