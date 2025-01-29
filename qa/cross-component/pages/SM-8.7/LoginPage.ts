import {Page, Locator, expect} from '@playwright/test';

class LoginPage {
  private page: Page;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly loginButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usernameInput = page.getByLabel('Username or email');
    this.passwordInput = page.getByLabel('Password');
    this.loginButton = page.getByRole('button', {name: 'Log in'});
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

  async login(username: string, password: string) {
    await expect(this.usernameInput).toBeVisible({timeout: 180000});
    await this.clickUsername();
    await this.fillUsername(username);
    await this.fillPassword(password);
    await expect(this.loginButton).toBeVisible({timeout: 120000});
    await this.clickLoginButton();
  }

  async loginAfterPermissionsReadded(username: string, password: string) {
    if (await this.usernameInput.isVisible({timeout: 60000})) {
      await this.clickUsername();
      await this.fillUsername(username);
      await this.fillPassword(password);
      await expect(this.loginButton).toBeVisible({timeout: 120000});
      await this.clickLoginButton();
    }
  }
}
export {LoginPage};
