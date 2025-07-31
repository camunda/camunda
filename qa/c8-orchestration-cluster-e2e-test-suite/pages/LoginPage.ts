/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';

export class LoginPage {
  private page: Page;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly loginButton: Locator;
  readonly errorMessage: Locator;
  readonly tasklistHeading: Locator;
  readonly operateHeading: Locator;
  readonly identityHeading: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usernameInput = page.getByLabel('Username');
    this.passwordInput = page.getByRole('textbox', {name: 'password'});
    this.loginButton = page.getByRole('button', {name: 'Login'});
    this.errorMessage = page.locator('.cds--inline-notification__title');
    this.tasklistHeading = page.getByRole('heading', {name: 'Tasklist'});
    this.operateHeading = page.getByRole('heading', {name: 'Operate'});
    this.identityHeading = page.getByRole('heading', {name: 'Identity'});
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

  async expectInvalidCredentialsError(): Promise<void> {
    await expect(this.errorMessage).toContainText(
      /Username and [Pp]assword do(?: not|n't) match/,
    );
  }
}
