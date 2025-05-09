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

import { IS_ROLES_BASE_PAGE_INTEGRATED } from "../../src/feature-flags";

const NEW_Role = {
  id: "testRoleID",
  name: "Test role",
};

test.beforeEach(async ({ page, loginPage }) => {
  await page.goto(relativizePath(Paths.roles()));
  await loginPage.login(LOGIN_CREDENTIALS);
  await expect(page).toHaveURL(relativizePath(Paths.roles()));
});

test.describe[IS_ROLES_BASE_PAGE_INTEGRATED ? "serial" : "skip"](
  "roles CRUD",
  () => {
    test("creates a role", async ({ page, rolesPage }) => {
      await expect(
        rolesPage.rolesList.getByRole("cell", { name: "Admin" }),
      ).toBeVisible();

      await rolesPage.createRoleButton.click();
      await expect(rolesPage.createRoleModal).toBeVisible();
      await rolesPage.createIdField.fill(NEW_Role.id);
      await rolesPage.createNameField.fill(NEW_Role.name);
      await rolesPage.createRoleModalCreateButton.click();
      await expect(rolesPage.createRoleModal).not.toBeVisible();

      const item = rolesPage.rolesList.getByRole("cell", {
        name: NEW_Role.name,
      });

      await waitForItemInList(page, item);
    });

    test("deletes a role", async ({ page, rolesPage }) => {
      await expect(
        rolesPage.rolesList.getByRole("cell", { name: NEW_Role.name }),
      ).toBeVisible();

      await rolesPage.deleteRoleButton(NEW_Role.name).click();
      await expect(rolesPage.deleteRoleModal).toBeVisible();
      await rolesPage.deleteRoleModalDeleteButton.click();
      await expect(rolesPage.deleteRoleModal).not.toBeVisible();

      const item = rolesPage.rolesList.getByRole("cell", {
        name: NEW_Role.name,
      });

      await waitForItemInList(page, item, false);
    });
  },
);
