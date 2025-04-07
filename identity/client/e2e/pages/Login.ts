/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { Page, Locator } from "@playwright/test";
import { Paths } from "../utils/paths";

export class Login {
  private page: Page;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly loginButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usernameInput = page.getByLabel(/^username$/i);
    this.passwordInput = page.getByLabel(/^password$/i);
    this.loginButton = page.getByRole("button", { name: "Login" });
  }

  async login(credentials: { username: string; password: string }) {
    const { username, password } = credentials;

    await this.usernameInput.fill(username);
    await this.passwordInput.fill(password);
    await this.loginButton.click();
  }

  async navigateToLogin() {
    await this.page.goto(Paths.login());
  }
}
