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

const NEW_TENANT = {
  tenantId: "testTenantId",
  name: "Test Tenant",
  description: "test description",
};

const USER = {
  id: "demo",
  name: "Demo",
};

test.beforeEach(async ({ loginPage, tenantsPage }) => {
  await tenantsPage.navigateToTenants();
  await loginPage.login(LOGIN_CREDENTIALS);
});

// this is skipped as it is not currently possible to run e2e tests in isTenantsApiEnabled mode.
// TODO: renable when https://github.com/camunda/camunda/issues/36092 is implemented
test.describe.skip("tenants CRUD", () => {
  test("creates a tenant", async ({ page, tenantsPage }) => {
    await expect(
      tenantsPage.tenantsList.getByRole("cell", { name: NEW_TENANT.name }),
    ).not.toBeVisible();

    await tenantsPage.createTenantButton.click();
    await expect(tenantsPage.createTenantModal).toBeVisible();
    await tenantsPage.createTenantIdField.fill(NEW_TENANT.tenantId);
    await tenantsPage.createTenantNameField.fill(NEW_TENANT.name);
    await tenantsPage.createTenantDescField.fill(NEW_TENANT.description);
    await tenantsPage.createTenantModalCreateButton.click();
    await expect(tenantsPage.createTenantModal).not.toBeVisible();

    const item = tenantsPage.tenantsList.getByRole("cell", {
      name: NEW_TENANT.name,
    });

    await waitForItemInList(page, item);
  });

  test("assign a user", async ({ page, tenantsPage }) => {
    await tenantsPage.openTenantDetails(NEW_TENANT.tenantId).click();

    await tenantsPage.assignUserButton.click();
    await expect(tenantsPage.assignUserModal).toBeVisible();

    await tenantsPage.assignUserNameField.fill(USER.name);

    await tenantsPage.assignUserNameOption(USER.id).click();
    await tenantsPage.confirmAssignmentButton.click();
    await expect(tenantsPage.assignUserModal).not.toBeVisible();

    const item = tenantsPage.tenantsList.getByRole("row", {
      name: USER.name,
    });

    await waitForItemInList(page, item, {
      emptyStateLocator: tenantsPage.usersEmptyState,
    });
  });

  test("remove a user", async ({ page, tenantsPage }) => {
    await tenantsPage.openTenantDetails(NEW_TENANT.tenantId).click();
    await tenantsPage.removeUserButton(USER.name).click();
    await expect(tenantsPage.removeUserModal).toBeVisible();

    await tenantsPage.confirmRemoveUserButton.click();
    await expect(tenantsPage.removeUserModal).not.toBeVisible();

    const item = tenantsPage.tenantsList.getByRole("row", {
      name: USER.name,
    });

    await waitForItemInList(page, item, {
      emptyStateLocator: tenantsPage.usersEmptyState,
      shouldBeVisible: false,
    });
  });

  test("deletes a tenant", async ({ page, tenantsPage }) => {
    await expect(
      tenantsPage.tenantsList.getByRole("cell", { name: NEW_TENANT.name }),
    ).toBeVisible();

    await tenantsPage.deleteTenantButton(NEW_TENANT.name).click();
    await expect(tenantsPage.deleteTenantModal).toBeVisible();
    await tenantsPage.deleteTenantModalDeleteButton.click();
    await expect(tenantsPage.deleteTenantModal).not.toBeVisible();

    const item = tenantsPage.tenantsList.getByRole("row", {
      name: NEW_TENANT.name,
    });

    await waitForItemInList(page, item, { shouldBeVisible: false });
  });
});
