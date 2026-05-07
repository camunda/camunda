/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect} from '@playwright/test';
import {waitForAssertion} from '../utils/waitForAssertion';

export class LoginPage {
  private page: Page;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly loginButton: Locator;
  readonly errorMessage: Locator;
  readonly invalidCredentialsError: Locator;
  readonly tasklistHeading: Locator;
  readonly operateHeading: Locator;
  readonly identityHeading: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usernameInput = page.getByRole('textbox', {name: 'Username'});
    this.passwordInput = page.getByRole('textbox', {name: 'password'});
    this.loginButton = page.getByRole('button', {name: 'Login'});
    this.errorMessage = page.locator('.cds--inline-notification__title');
    this.invalidCredentialsError = page
      .getByRole('alert')
      .getByText(/Username and [Pp]assword do(?: not|n't) match/);
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
    // Camunda apps share an auth session: navigating to /<app>/login when
    // already authenticated redirects to the app home, so the login form
    // never renders. Wait briefly for any pending redirect to settle, and
    // if the page leaves /login skip login rather than spending the full
    // form-visibility retry budget waiting for an input that will never appear.
    try {
      await this.page.waitForURL((url) => !url.pathname.includes('/login'), {
        timeout: 5000,
      });
      return;
    } catch {
      // Still at /login — fall through to the normal login flow.
    }
    await waitForAssertion({
      assertion: async () => {
        await expect(this.usernameInput).toBeVisible({timeout: 30000});
      },
      onFailure: async () => {
        await this.page.reload();
      },
    });
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
