/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { expect } from "@playwright/test";
import { test } from "../text-fixtures";
import { LOGIN_CREDENTIALS } from "../utils/constants";
import { waitForItemInList } from "../utils/waitForItemInList";
import { relativizePath } from "../utils/relativizePaths";
import { Paths } from "../utils/paths";

test.beforeEach(async ({ page, loginPage, authorizationsPage }) => {
  await authorizationsPage.navigateToAuthorizations();
  await loginPage.login(LOGIN_CREDENTIALS);
  await expect(page).toHaveURL(relativizePath(Paths.authorizations()));
});

const NEW_USER = {
  username: "testuser",
  name: "Test User",
  email: "testuser@camunda.com",
  password: "testpassword",
};

const NEW_AUTHORIZATION = {
  owner: NEW_USER.name,
  ownerId: NEW_USER.username,
  resourceId: "identity",
};

test.describe.serial("authorizations CRUD", () => {
  test("create an authorization", async ({
    page,
    authorizationsPage,
    usersPage,
    header,
    loginPage,
  }) => {
    await usersPage.navigateToUsers();
    await usersPage.createUser(NEW_USER);

    await header.logout();
    await loginPage.login(NEW_USER);
    await expect(page).toHaveURL(relativizePath(Paths.forbidden()));
    await header.logout();
    await loginPage.login(LOGIN_CREDENTIALS);
    await expect(page).toHaveURL(relativizePath(Paths.users()));
    await authorizationsPage.navigateToAuthorizations();
    await expect(page).toHaveURL(relativizePath(Paths.authorizations()));
    await authorizationsPage.createAuthorizationButton.click();
    await expect(authorizationsPage.createAuthorizationModal).toBeVisible();
    await authorizationsPage.createAuthorizationOwnerComboBox.click();
    await authorizationsPage
      .createAuthorizationOwnerOption(NEW_AUTHORIZATION.owner)
      .click();
    await authorizationsPage.createAuthorizationResourceIdField.fill(
      NEW_AUTHORIZATION.resourceId,
    );
    await authorizationsPage
      .createAuthorizationAccessPermission("Access")
      .check({ force: true });
    await authorizationsPage.createAuthorizationSubmitButton.click();

    const newAuthorizationItem =
      authorizationsPage.authorizationsList.getByRole("cell", {
        name: NEW_AUTHORIZATION.ownerId,
      });

    await waitForItemInList(page, newAuthorizationItem);

    await header.logout();
    await loginPage.login(NEW_USER);
    await expect(page).toHaveURL(relativizePath(Paths.users()));
  });

  test("delete an authorization", async ({
    page,
    header,
    loginPage,
    usersPage,
    authorizationsPage,
  }) => {
    await expect(
      authorizationsPage.authorizationsList.getByRole("cell", {
        name: NEW_AUTHORIZATION.ownerId,
      }),
    ).toBeVisible();

    await authorizationsPage
      .deleteAuthorizationButton(NEW_AUTHORIZATION.ownerId)
      .click();
    await expect(authorizationsPage.deleteAuthorizationModal).toBeVisible();
    await authorizationsPage.deleteAuthorizationModalDeleteButton.click();
    await expect(authorizationsPage.deleteAuthorizationModal).not.toBeVisible();

    const item = authorizationsPage.authorizationsList.getByRole("cell", {
      name: NEW_AUTHORIZATION.ownerId,
    });

    await waitForItemInList(page, item, { shouldBeVisible: false });
    await header.logout();
    await loginPage.login(NEW_USER);
    await expect(page).toHaveURL(relativizePath(Paths.forbidden()));
    await header.logout();
    await loginPage.login(LOGIN_CREDENTIALS);
    await usersPage.deleteUser(NEW_USER.name);
  });
});
