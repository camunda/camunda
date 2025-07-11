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
import { waitForItemInList } from "../utils/waitForItemInList";

const NEW_ROLE = {
  id: "testRoleID",
  name: "Test role",
};

test.beforeEach(async ({ page, loginPage }) => {
  await page.goto(relativizePath(Paths.roles()));
  await loginPage.login(LOGIN_CREDENTIALS);
  await expect(page).toHaveURL(relativizePath(Paths.roles()));
});

test.describe.serial("roles CRUD", () => {
  test("creates a role", async ({ page, rolesPage }) => {
    await expect(
      rolesPage.rolesList.getByRole("cell", { name: "Admin", exact: true }),
    ).toBeVisible();

    await rolesPage.createRole(NEW_ROLE);
  });

  test("deletes a role", async ({ page, rolesPage }) => {
    await expect(
      rolesPage.rolesList.getByRole("cell", { name: NEW_ROLE.name }),
    ).toBeVisible();

    await rolesPage.deleteRole(NEW_ROLE.name);
  });
});
