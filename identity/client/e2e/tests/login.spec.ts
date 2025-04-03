/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { expect } from "@playwright/test";
import { loginTest as test } from "../text-fixtures";

test.beforeEach(async ({ loginPage }) => {
  await loginPage.navigateToLogin();
});

test.describe("login page", () => {
  test("Log in with valid user account", async ({ loginPage, page }) => {
    await loginPage.login({
      username: "demo",
      password: "demo",
    });

    await expect(page).toHaveURL("../identity/users");
  });
});
