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

const NEW_USER = {
  username: "testuser",
  name: "Test User",
  email: "testuser@camunda.com",
  password: "testpassword",
};

const EDITED_USER = {
  name: "Edited User",
  email: "editeduser@camunda.com",
  password: "editedtestpassword",
};

test.beforeEach(async ({ page, loginPage }) => {
  await loginPage.navigateToLogin();
  await loginPage.login(LOGIN_CREDENTIALS);
  await expect(page).toHaveURL(relativizePath(Paths.users()));
});

test.describe.serial("users CRUD", () => {
  test("creates a user", async ({ page, usersPage }) => {
    await expect(
      usersPage.usersList.getByRole("cell", { name: "demo@example.com" }),
    ).toBeVisible();
    await expect(
      usersPage.usersList.getByRole("cell", { name: NEW_USER.email }),
    ).not.toBeVisible();

    await usersPage.createUserButton.click();
    await expect(usersPage.createUserModal).toBeVisible();
    await usersPage.createUsernameField.fill(NEW_USER.username);
    await usersPage.createNameField.fill(NEW_USER.name);
    await usersPage.createEmailField.fill(NEW_USER.email);
    await usersPage.createPasswordField.fill(NEW_USER.password);
    await usersPage.createRepeatPasswordField.fill(NEW_USER.password);
    await usersPage.createUserModalCreateButton.click();
    await expect(usersPage.createUserModal).not.toBeVisible();

    const item = usersPage.usersList.getByRole("cell", {
      name: NEW_USER.email,
    });

    await waitForItemInList(page, item);
  });

  test("edits a user", async ({ page, usersPage }) => {
    await expect(
      usersPage.usersList.getByRole("cell", { name: NEW_USER.email }),
    ).toBeVisible();

    await usersPage.editUserButton(NEW_USER.email).click();
    await expect(usersPage.editUserModal).toBeVisible();
    await usersPage.editNameField.fill(EDITED_USER.name);
    await usersPage.editEmailField.fill(EDITED_USER.email);
    await usersPage.editUserModalUpdateButton.click();
    await expect(usersPage.editUserModal).not.toBeVisible();

    const item = usersPage.usersList.getByRole("cell", {
      name: EDITED_USER.email,
    });

    await waitForItemInList(page, item);
  });

  test("deletes a user", async ({ page, usersPage }) => {
    await expect(
      usersPage.usersList.getByRole("cell", { name: EDITED_USER.email }),
    ).toBeVisible();

    await usersPage.deleteUserButton(EDITED_USER.email).click();
    await expect(usersPage.deleteUserModal).toBeVisible();
    await usersPage.deleteUserModalDeleteButton.click();
    await expect(usersPage.deleteUserModal).not.toBeVisible();

    const item = usersPage.usersList.getByRole("cell", {
      name: EDITED_USER.email,
    });

    await waitForItemInList(page, item, false);
  });
});
