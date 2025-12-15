/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {createTestData, LOGIN_CREDENTIALS} from 'utils/constants';
import {navigateToApp} from '@pages/UtilitiesPage';
import {waitForItemInList} from 'utils/waitForItemInList';
import {Paths, relativizePath} from 'utils/relativizePath';

const USER = {
  id: 'demo',
  name: 'Demo',
};

test.describe.serial('cluster variables CRUD', () => {
  let NEW_CLUSTER_VARIABLE: NonNullable<ReturnType<typeof createTestData>['clusterVariable']>;

  test.beforeAll(() => {
    const testData = createTestData({
      clusterVariable: true,
    });
    NEW_CLUSTER_VARIABLE = testData.clusterVariable!;
  });

  test.beforeEach(async ({page, loginPage, identityHeader}) => {
    await navigateToApp(page, 'identity');
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );
    await expect(page).toHaveURL(relativizePath(Paths.users()));
    await identityHeader.navigateToClusterVariables();
    await expect(page).toHaveURL(relativizePath(Paths.clusterVariables()));
  });

  test.describe.serial('cluster variables CRUD', () => {
    test('tries to create a cluster variable with invalid id', async ({
      identityClusterVariablesPage,
    }) => {
      await identityClusterVariablesPage.createTenantButton.click();
      await identityClusterVariablesPage.fillTenantId('invalid!!%');
      await expect(identityClusterVariablesPage.createTenantModal).toContainText(
        'Please enter a valid Tenant ID',
      );
      await expect(identityClusterVariablesPage.tenantFieldId).toHaveAttribute(
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
