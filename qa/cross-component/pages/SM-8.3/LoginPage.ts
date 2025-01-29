import {Page, Locator, expect} from '@playwright/test';

class LoginPage {
  private page: Page;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly loginButton: Locator;
  readonly continueButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usernameInput = page.locator('[id="username"]');
    this.passwordInput = page.locator('[id="password"]');
    this.loginButton = page.getByRole('button', {name: 'Log in'});
    this.continueButton = page.getByRole('button', {
      name: 'Continue',
      exact: true,
    });
  }

  async fillUsername(username: string): Promise<void> {
    await this.usernameInput.fill(username);
  }

  async clickUsername(): Promise<void> {
    await this.usernameInput.click({timeout: 60000});
  }

  async fillPassword(password: string): Promise<void> {
    await this.passwordInput.fill(password);
  }

  async clickLoginButton(): Promise<void> {
    await this.loginButton.click({timeout: 60000});
  }

  async clickContinueButton(): Promise<void> {
    await this.continueButton.click({timeout: 60000});
  }

  async login(username: string, password: string) {
    try {
      await expect(this.usernameInput).toBeVisible({timeout: 180000});
      await this.clickUsername();
      await this.fillUsername(username);
      await this.fillPassword(password);
      await expect(this.loginButton).toBeVisible({timeout: 120000});
      await this.clickLoginButton();
    } catch {
      await this.page.reload();
      await expect(this.usernameInput).toBeVisible({timeout: 180000});
      await this.clickUsername();
      await this.fillUsername(username);
      await this.fillPassword(password);
      await expect(this.loginButton).toBeVisible({timeout: 120000});
      await this.clickLoginButton();
    }
  }

  async loginAfterPermissionsReadded(username: string, password: string) {
    if (await this.usernameInput.isVisible({timeout: 60000})) {
      this.login(username, password);
    }
  }
}
export {LoginPage};
