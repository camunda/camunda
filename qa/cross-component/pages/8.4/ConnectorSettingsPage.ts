import {Page, Locator} from '@playwright/test';

class ConnectorSettingsPage {
  private page: Page;
  readonly urlInput: Locator;
  readonly resultExpressionInput: Locator;
  readonly authenticationTypeDropdown: Locator;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly methodTypeDropdown: Locator;
  readonly resultVariableInput: Locator;
  readonly bearerTokenInput: Locator;

  constructor(page: Page) {
    this.page = page;
    this.urlInput = page.getByLabel('URL');
    this.resultExpressionInput = page
      .locator('div')
      .filter({hasText: /^Result expression=Opened in editor$/})
      .getByRole('textbox');
    this.authenticationTypeDropdown = page.getByLabel('Type');
    this.usernameInput = page.getByLabel('Username');
    this.passwordInput = page.getByLabel('Password');
    this.methodTypeDropdown = page.getByLabel('Method');
    this.resultVariableInput = page.getByLabel('Result variable');
    this.bearerTokenInput = page.getByLabel('Bearer token');
  }

  async clickUrlInput(): Promise<void> {
    await this.urlInput.click({timeout: 90000});
  }

  async fillUrlInput(url: string): Promise<void> {
    await this.urlInput.fill(url, {timeout: 90000});
  }

  async clickResultExpressionInput(): Promise<void> {
    await this.resultExpressionInput.click({timeout: 90000});
  }

  async fillResultExpressionInput(resultExpression: string): Promise<void> {
    await this.resultExpressionInput.fill(resultExpression, {timeout: 90000});
  }

  async selectAuthenticationType(authentication: string): Promise<void> {
    await this.authenticationTypeDropdown.selectOption(authentication, {
      timeout: 60000,
    });
  }

  async clickUsernameInput(): Promise<void> {
    await this.usernameInput.click({timeout: 90000});
  }

  async fillUsernameInput(username: string): Promise<void> {
    await this.usernameInput.fill(username, {timeout: 60000});
  }

  async clickPasswordInput(): Promise<void> {
    await this.passwordInput.click({timeout: 60000});
  }

  async fillPasswordInput(password: string): Promise<void> {
    await this.passwordInput.fill(password, {timeout: 60000});
  }

  async selectMethodType(method: string): Promise<void> {
    await this.methodTypeDropdown.selectOption(method, {timeout: 60000});
  }

  async clickResultVariableInput(): Promise<void> {
    await this.resultVariableInput.click({timeout: 60000});
  }

  async fillResultVariableInput(variable: string): Promise<void> {
    await this.resultVariableInput.fill(variable, {timeout: 90000});
  }

  async clickBearerTokenInput(): Promise<void> {
    await this.bearerTokenInput.click({timeout: 60000});
  }

  async fillBearerTokenInput(token: string): Promise<void> {
    await this.bearerTokenInput.fill(token, {timeout: 60000});
  }
}

export {ConnectorSettingsPage};
