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

test.beforeEach(async ({ loginPage, page }) => {
  await loginPage.navigateToLogin();
  await loginPage.login(LOGIN_CREDENTIALS);
  await expect(page).toHaveURL(relativizePath(Paths.users()));
});

test.describe.only("users CRUD", () => {
  test("creates an user", async ({ usersPage }) => {
    await usersPage.createUserButton.click();
  });
  // @TODO: fill form fields
  // @TODO: click create user button
  // @TODO: await for user to be created
  // @TODO: refresh page
  // @TODO: check for new user on list
});
