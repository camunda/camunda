/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { expect } from "@playwright/test";
import { test } from "../text-fixtures";
import { Paths } from "../utils/paths";
import { relativizePath } from "../utils/relativizePaths";
import { LOGIN_CREDENTIALS } from "../utils/constants";

test.beforeEach(async ({ loginPage }) => {
  await loginPage.navigateToLogin();
});

test.describe("login page", () => {
  test("Log in with invalid user account", async ({ loginPage, page }) => {
    expect(await loginPage.passwordInput.getAttribute("type")).toEqual(
      "password",
    );

    await loginPage.login({
      username: "demo",
      password: "wrong-password",
    });

    // TODO: add this back when login validation is implemented - https://github.com/camunda/camunda/issues/29849
    // await expect(
    //   page.getByRole("alert").getByText("Username and password do not match"),
    // ).toBeVisible();

    await expect(page).toHaveURL(relativizePath(Paths.login()));
  });

  test("Log in with valid user account", async ({ loginPage, page }) => {
    await loginPage.login(LOGIN_CREDENTIALS);

    await expect(page).toHaveURL(relativizePath(Paths.users()));
  });

  test("Log out", async ({ loginPage, header, page }) => {
    await loginPage.login(LOGIN_CREDENTIALS);

    await expect(page).toHaveURL(relativizePath(Paths.users()));
    await header.logout();
    await expect(page).toHaveURL(
      `${relativizePath(Paths.login())}?next=/identity/`,
    );
  });

  test("Redirect to initial page after login", async ({ loginPage, page }) => {
    await expect(page).toHaveURL(relativizePath(Paths.login()));
    await page.goto(relativizePath(Paths.users()));
    await expect(page).toHaveURL(
      `${relativizePath(Paths.login())}?next=/identity${Paths.users()}`,
    );

    await loginPage.login(LOGIN_CREDENTIALS);

    await expect(page).toHaveURL(relativizePath(Paths.users()));
  });
});
