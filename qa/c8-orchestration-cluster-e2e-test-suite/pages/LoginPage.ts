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
    // The admin/login page renders its error via Carbon's InlineNotification
    // (`.cds--inline-notification__title`) on the legacy design system and via
    // the new design system's Alert (`[data-slot="alert-title"]`) once
    // IS_NEW_DESIGN_SYSTEM_ENABLED is on. Match either so the assertion works
    // regardless of which design system the build ships.
    this.errorMessage = page.locator(
      '.cds--inline-notification__title, [data-slot="alert-title"]',
    );
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
    // The orchestration-cluster session cookie is shared across Operate,
    // Tasklist and Identity, so navigating to a "/<app>/login" URL while a
    // valid session already exists redirects to the app home and renders no
    // login form. Treat login() as idempotent in exactly that case.
    // First-login and invalid-credentials tests always render the form (they
    // start from a fresh, unauthenticated context), so they are unaffected.
    try {
      await waitForAssertion({
        assertion: async () => {
          await expect(this.usernameInput).toBeVisible({timeout: 15000});
        },
        onFailure: async () => {
          await this.page.reload();
        },
        maxRetries: 2,
      });
    } catch (error) {
      // A missing form only means "already signed in" if the app has actually
      // navigated off the login route. Still being on /login means the form
      // genuinely failed to render — a slow shell, an app that is not up, a
      // wrong URL — and swallowing that would resurface as a confusing failure
      // somewhere later in the test, so let the original error through.
      if (new URL(this.page.url()).pathname.endsWith('/login')) {
        throw error;
      }
      return;
    }
    await this.clickUsername();
    await this.fillUsername(username);
    await this.fillPassword(password);
    await expect(this.loginButton).toBeVisible({timeout: 120000});
    // The login POST sets the session cookie via its response. The SPA can
    // change the URL optimistically before that response lands, so a hard
    // navigation fired right after the click races the cookie write and
    // bounces back to the login page. Wait for the POST /login response to
    // complete so the cookie is committed before any subsequent navigation.
    await Promise.all([
      this.page.waitForResponse(
        (response) =>
          response.url().includes('/login') &&
          response.request().method() === 'POST',
        {timeout: 60000},
      ),
      this.clickLoginButton(),
    ]);
  }

  async expectInvalidCredentialsError(): Promise<void> {
    await expect(this.errorMessage).toContainText(
      /Username and [Pp]assword do(?: not|n't) match/,
    );
  }
}
