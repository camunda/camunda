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

  async clickOpenSettingsButton(retries: number = 3): Promise<void> {
    for (let i = 0; i < retries; i++) {
      try {
        await this.openSettingsButton.click({timeout: 180000});
        return; // If successful, exit the function
      } catch (error) {
        const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
        await sleep(10000);
      }
    }
    throw new Error(`Failed to click button after ${retries} retries`);
  }

  async clickLogoutButton(retries: number = 3): Promise<void> {
    for (let i = 0; i < retries; i++) {
      try {
        await this.logoutButton.click({timeout: 120000});
        return; // Click succeeded, exit the loop
      } catch (error) {
        console.error(
          `Attempt ${i + 1} to click deploy button failed: ${error}`,
        );
        // Wait for 10 seconds
        const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
        await sleep(10000);
        await this.clickOpenSettingsButton();
      }
    }
    throw new Error(`Failed to click deploy button after ${retries} retries`);
  }
}

export {SettingsPage};
