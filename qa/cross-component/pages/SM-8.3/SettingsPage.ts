import {Page, Locator} from '@playwright/test';

class SettingsPage {
  private page: Page;
  readonly openSettingsButton: Locator;
  readonly logoutButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.openSettingsButton = page.getByLabel('Open Settings');
    this.logoutButton = page.getByRole('button', {name: 'Log out'});
  }

  async clickOpenSettingsButton(): Promise<void> {
    await this.openSettingsButton.click({timeout: 120000});
  }

  async clickLogoutButton(): Promise<void> {
    await this.logoutButton.click({timeout: 30000});
  }
}

export {SettingsPage};
