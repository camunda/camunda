import {Page, Locator} from '@playwright/test';
import {ModelerCreatePage} from './ModelerCreatePage';

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
    try {
      await this.urlInput.click({timeout: 60000});
    } catch {
      const modelerCreatePage = new ModelerCreatePage(this.page);
      await modelerCreatePage.clickStartEventElement();
      await modelerCreatePage.clickAppendTaskButton();
      await modelerCreatePage.clickChangeTypeButton();
      await modelerCreatePage.clickRestConnectorOption();
      await this.urlInput.click({timeout: 60000});
    }
  }

  async fillUrlInput(url: string): Promise<void> {
    await this.urlInput.fill(url);
  }

  async clickResultExpressionInput(): Promise<void> {
    await this.resultExpressionInput.click();
  }

  async fillResultExpressionInput(resultExpression: string): Promise<void> {
    await this.resultExpressionInput.fill(resultExpression);
  }

  async selectAuthenticationType(authentication: string): Promise<void> {
    await this.authenticationTypeDropdown.selectOption(authentication);
  }

  async clickUsernameInput(): Promise<void> {
    await this.usernameInput.click({timeout: 30000});
  }

  async fillUsernameInput(username: string): Promise<void> {
    await this.usernameInput.fill(username, {timeout: 30000});
  }

  async clickPasswordInput(): Promise<void> {
    await this.passwordInput.click();
  }

  async fillPasswordInput(password: string): Promise<void> {
    await this.passwordInput.fill(password);
  }

  async selectMethodType(method: string): Promise<void> {
    await this.methodTypeDropdown.selectOption(method, {timeout: 60000});
  }

  async clickResultVariableInput(): Promise<void> {
    await this.resultVariableInput.click();
  }

  async fillResultVariableInput(variable: string): Promise<void> {
    await this.resultVariableInput.fill(variable);
  }

  async clickBearerTokenInput(): Promise<void> {
    await this.bearerTokenInput.click({timeout: 30000});
  }

  async fillBearerTokenInput(token: string): Promise<void> {
    await this.bearerTokenInput.fill(token);
  }
}

export {ConnectorSettingsPage};
