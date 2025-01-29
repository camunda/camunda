import {Page, Locator, expect} from '@playwright/test';
import {assertTestUsesCorrectOrganization} from './UtilitiesPage';

class LoginPage {
  private page: Page;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly continueButton: Locator;
  readonly loginButton: Locator;
  readonly loginMessage: Locator;
  readonly errorMessage: Locator;
  readonly passwordHeading: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usernameInput = page.getByLabel('Email address');
    this.passwordInput = page.getByLabel('Password');
    this.continueButton = page.getByRole('button', {
      name: 'Continue',
      exact: true,
    });
    this.loginMessage = page.getByText('Log in to continue to Camunda.');
    this.passwordHeading = page.getByRole('heading', {
      name: 'Enter Your Password',
    });
    this.loginButton = page.getByRole('button', {
      name: 'Continue',
      exact: true,
    });
    this.errorMessage = page.getByText('Wrong email or password');
  }

  async fillUsername(username: string): Promise<void> {
    await this.usernameInput.fill(username);
  }

  async clickContinueButton(): Promise<void> {
    await this.continueButton.click();
  }

  async fillPassword(password: string): Promise<void> {
    await this.passwordInput.fill(password);
  }

  async clickLoginButton(): Promise<void> {
    await this.loginButton.click({timeout: 60000});
  }

  async login(credentials: {username?: string; password?: string} = {}) {
    const {
      username = process.env.C8_USERNAME!,
      password = process.env.C8_PASSWORD!,
    } = credentials;

    await expect(this.usernameInput).toBeVisible({timeout: 120000});
    await this.fillUsername(username);
    await this.clickContinueButton();
    await this.fillPassword(password);
    await expect(this.loginButton).toBeVisible({timeout: 120000});
    await this.clickLoginButton();
    await assertTestUsesCorrectOrganization(this.page);
  }

  async loginWithTestUser(
    credentials: {username?: string; password?: string} = {},
    skipOrgAssertion?: boolean,
  ) {
    const {
      username = process.env.C8_USERNAME_TEST!,
      password = process.env.C8_PASSWORD_TEST!,
    } = credentials;
    await expect(this.usernameInput).toBeVisible({timeout: 120000});
    await this.fillUsername(username);
    await this.clickContinueButton();
    await this.fillPassword(password);
    await expect(this.loginButton).toBeVisible({timeout: 120000});
    await this.clickLoginButton();
    if (skipOrgAssertion == true) {
      return;
    }
    await assertTestUsesCorrectOrganization(this.page);
  }

  async loginFailAssertion(): Promise<void> {
    try {
      await expect(this.errorMessage).toContainText('Wrong email or password');
    } catch (error) {
      await expect(
        this.page.getByText(
          'Your account has been blocked after multiple consecutive login attempts',
        ),
      ).toBeVisible({timeout: 20000});
    }
  }

  async loginWithoutOrgAssertion(
    credentials: {username?: string; password?: string} = {},
  ) {
    const {
      username = process.env.C8_USERNAME!,
      password = process.env.C8_PASSWORD!,
    } = credentials;

    await expect(this.usernameInput).toBeVisible({timeout: 120000});
    await this.fillUsername(username);
    await this.clickContinueButton();
    await this.fillPassword(password);
    await expect(this.loginButton).toBeVisible({timeout: 120000});
    await this.clickLoginButton();
  }
}
export {LoginPage};
