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
    await navigateToApp(page, 'admin');
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );
    await expect(page).toHaveURL(relativizePath(Paths.users()));
    await identityHeader.navigateToTenants();
    await expect(page).toHaveURL(relativizePath(Paths.tenants()));
  });

  test.describe.serial('tenants CRUD', () => {
    test('tries to create a tenant with invalid id', async ({
      identityTenantsPage,
    }) => {
      await identityTenantsPage.createTenantButton.click();
      await identityTenantsPage.fillTenantId('invalid!!%');
      await expect(identityTenantsPage.createTenantModal).toContainText(
        'Please enter a valid Tenant ID',
      );
      await expect(identityTenantsPage.tenantFieldId).toHaveAttribute(
        'data-invalid',
        'true',
      );
    });

    test('creates a tenant', async ({page, identityTenantsPage}) => {
      await expect(
        identityTenantsPage.tenantCell(NEW_TENANT.name),
      ).toBeHidden();
      await identityTenantsPage.createTenant(NEW_TENANT);
      const item = identityTenantsPage.tenantCell(NEW_TENANT.name);
      await waitForItemInList(page, item, {timeout: 60000, clickNext: true});
    });

    test('assign a user', async ({page, identityTenantsPage}) => {
      await waitForItemInList(
        page,
        identityTenantsPage.tenantCell(NEW_TENANT.name),
        {timeout: 60000, clickNext: true},
      );
      await identityTenantsPage.openTenantDetails(NEW_TENANT.tenantId).click();
      await identityTenantsPage.assignUserToTenant(USER);
      const item = identityTenantsPage.userRow(USER.name);
      await waitForItemInList(page, item, {
        emptyStateLocator: identityTenantsPage.usersEmptyState,
        timeout: 60000,
      });
    });

    test('remove a user', async ({page, identityTenantsPage}) => {
      await waitForItemInList(
        page,
        identityTenantsPage.tenantCell(NEW_TENANT.name),
        {timeout: 60000, clickNext: true},
      );
      await identityTenantsPage.openTenantDetails(NEW_TENANT.tenantId).click();
      await identityTenantsPage.removeUserFromTenant(USER.name);
      const item = identityTenantsPage.userRow(USER.name);
      await waitForItemInList(page, item, {
        emptyStateLocator: identityTenantsPage.usersEmptyState,
        shouldBeVisible: false,
      });
    });

    test('delete a tenant', async ({page, identityTenantsPage}) => {
      const item = identityTenantsPage.tenantCell(NEW_TENANT.name);
      await waitForItemInList(page, item, {
        clickNext: true,
        timeout: 60000,
      });

      await identityTenantsPage.deleteTenant(NEW_TENANT.name);

      await waitForItemInList(page, item, {
        shouldBeVisible: false,
        clickNext: true,
        timeout: 60000,
      });
    });
  });
});
