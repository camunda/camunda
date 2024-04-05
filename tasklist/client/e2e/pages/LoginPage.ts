/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Page, Locator} from '@playwright/test';

class LoginPage {
  private page: Page;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly loginButton: Locator;
  readonly errorMessage: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usernameInput = page.getByLabel('Username');
    this.passwordInput = page.getByLabel('Password');
    this.loginButton = page.getByRole('button', {name: 'Login'});
    this.errorMessage = page.getByRole('alert').first();
  }

  async fillUsername(username: string) {
    await this.usernameInput.fill(username);
  }

  async fillPassword(password: string) {
    await this.passwordInput.fill(password);
  }

  async clickLoginButton() {
    await this.loginButton.click();
  }

  async login(credentials: {username: string; password: string}) {
    const {username, password} = credentials;

    await this.fillUsername(username);
    await this.fillPassword(password);
    await this.clickLoginButton();
  }

  async navigateToURL(url: string) {
    await this.page.goto(url);
  }
}

export {LoginPage};
