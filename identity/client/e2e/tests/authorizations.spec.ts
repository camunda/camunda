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
  username: "authtest",
  name: "Auth Test",
  email: "auth@test.com",
  password: "authtest123",
};

const NEW_AUTH_ROLE = {
  id: "authrole",
  name: "Auth role",
};

const NEW_USER_AUTHORIZATION = {
  ownerType: "Role",
  ownerId: NEW_AUTH_ROLE.name,
  resourceType: "User",
  resourceId: "*",
  accessPermissions: ["update", "create", "read", "delete"],
};

const NEW_APPLICATION_AUTHORIZATION = {
  ownerType: "Role",
  ownerId: NEW_AUTH_ROLE.name,
  resourceType: "Application",
  resourceId: "*",
  accessPermissions: ["access"],
};

test.describe.serial("authorizations CRUD", () => {
  test("create user authorization", async ({
    page,
    usersPage,
    rolesPage,
    rolesDetailsPage,
    authorizationsPage,
    header,
    loginPage,
  }) => {
    await usersPage.navigateToUsers();
    await usersPage.createUser(NEW_USER);

    await header.logout();

    await loginPage.login(NEW_USER);
    await expect(page).toHaveURL(relativizePath(Paths.users()));

    await expect(
      usersPage.usersList.getByRole("cell", { name: "demo@example.com" }),
    ).not.toBeVisible();
    await expect(
      usersPage.usersList.getByRole("cell", { name: NEW_USER.email }),
    ).toBeVisible();

    await header.logout();

    await loginPage.login(LOGIN_CREDENTIALS);
    await expect(page).toHaveURL(relativizePath(Paths.users()));
    await rolesPage.navigateToRoles();
    await expect(page).toHaveURL(relativizePath(Paths.roles()));

    await rolesPage.createRole(NEW_AUTH_ROLE);
    await rolesPage.rolesList.getByText(NEW_AUTH_ROLE.id).click();

    await rolesDetailsPage.assignUser(NEW_USER);

    await authorizationsPage.navigateToAuthorizations();
    await expect(page).toHaveURL(relativizePath(Paths.authorizations()));

    await authorizationsPage.createAuthorization(NEW_USER_AUTHORIZATION);

    await header.logout();
    await loginPage.login(NEW_USER);
    await expect(page).toHaveURL(relativizePath(Paths.forbidden()));
  });

  test("create application authorization", async ({
    usersPage,
    authorizationsPage,
    header,
    loginPage,
  }) => {
    await authorizationsPage.createAuthorization(NEW_APPLICATION_AUTHORIZATION);
    await header.logout();
    await loginPage.login(NEW_USER);
    await expect(
      usersPage.usersList.getByRole("cell", { name: NEW_USER.email }),
    ).toBeVisible();
    await expect(
      usersPage.usersList.getByRole("cell", { name: "demo@example.com" }),
    ).toBeVisible();
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
        name: NEW_AUTH_ROLE.id,
      }),
    ).toBeVisible();

    await authorizationsPage
      .deleteAuthorizationButton(NEW_AUTH_ROLE.id)
      .click();
    await expect(authorizationsPage.deleteAuthorizationModal).toBeVisible();
    await authorizationsPage.deleteAuthorizationModalDeleteButton.click();
    await expect(authorizationsPage.deleteAuthorizationModal).not.toBeVisible();

    const item = authorizationsPage.authorizationsList.getByRole("cell", {
      name: NEW_AUTH_ROLE.id,
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
