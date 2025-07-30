/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {LOGIN_CREDENTIALS, createTestData} from 'utils/constants';
import {navigateToApp} from '@pages/UtilitiesPage';
import {waitForItemInList} from 'utils/waitForItemInList';
import {relativizePath, Paths} from 'utils/relativizePath';

const USER = {
  id: 'demo',
  name: 'Demo',
};

test.describe.serial('tenants CRUD', () => {
  let NEW_TENANT: NonNullable<ReturnType<typeof createTestData>['tenant']>;

  test.beforeAll(() => {
    const testData = createTestData({
      tenant: true,
      user: true,
    });
    NEW_TENANT = testData.tenant!;
  });

  test.beforeEach(async ({page, loginPage, identityHeader}) => {
    await navigateToApp(page, 'identity');
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );
    await expect(page).toHaveURL(relativizePath(Paths.users()));
    await identityHeader.navigateToTenants();
    await expect(page).toHaveURL(relativizePath(Paths.tenants()));
  });

  test.describe.serial('tenants CRUD', () => {
    test('creates a tenant', async ({page, identityTenantsPage}) => {
      await expect(
        identityTenantsPage.tenantCell(NEW_TENANT.name),
      ).toBeHidden();
      await identityTenantsPage.createTenant(NEW_TENANT);
      const item = identityTenantsPage.tenantCell(NEW_TENANT.name);
      await waitForItemInList(page, item, {timeout: 60000});
    });

    test('assign a user', async ({page, identityTenantsPage}) => {
      await identityTenantsPage.openTenantDetails(NEW_TENANT.tenantId).click();
      await identityTenantsPage.assignUserToTenant(USER);
      const item = identityTenantsPage.userRow(USER.name);
      await waitForItemInList(page, item, {
        emptyStateLocator: identityTenantsPage.usersEmptyState,
        timeout: 60000,
      });
    });

    test('remove a user', async ({page, identityTenantsPage}) => {
      await identityTenantsPage.openTenantDetails(NEW_TENANT.tenantId).click();
      await identityTenantsPage.removeUserFromTenant(USER.name);
      const item = identityTenantsPage.userRow(USER.name);
      await waitForItemInList(page, item, {
        emptyStateLocator: identityTenantsPage.usersEmptyState,
        shouldBeVisible: false,
      });
    });

    test('delete a tenant', async ({page, identityTenantsPage}) => {
      await expect(
        identityTenantsPage.tenantCell(NEW_TENANT.name),
      ).toBeVisible();
      await identityTenantsPage.deleteTenant(NEW_TENANT.name);
      const item = identityTenantsPage.tenantRow(NEW_TENANT.name);
      await waitForItemInList(page, item, {shouldBeVisible: false});
    });
  });
});
