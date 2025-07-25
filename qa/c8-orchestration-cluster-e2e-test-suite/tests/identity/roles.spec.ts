/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {relativizePath, Paths} from 'utils/relativizePath';
import {LOGIN_CREDENTIALS, createTestData} from 'utils/constants';
import {navigateToApp} from '@pages/UtilitiesPage';
import {captureFailureVideo, captureScreenshot} from '@setup';

test.describe.serial('roles CRUD', () => {
  let NEW_ROLE: NonNullable<ReturnType<typeof createTestData>['authRole']>;

  test.beforeAll(() => {
    const testData = createTestData({
      authRole: true,
    });
    NEW_ROLE = testData.authRole!;
  });

  test.beforeEach(async ({page, loginPage, identityHeader}) => {
    await navigateToApp(page, 'identity');
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );
    await expect(page).toHaveURL(relativizePath(Paths.users()));
    await identityHeader.navigateToRoles();
    await expect(page).toHaveURL(relativizePath(Paths.roles()));
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('creates a role', async ({identityRolesPage}) => {
    await expect(identityRolesPage.roleCell('Admin')).toBeVisible();
    await identityRolesPage.createRole(NEW_ROLE);
  });

  test('deletes a role', async ({page, identityRolesPage}) => {
    const item = identityRolesPage.roleCell(NEW_ROLE.name);
    await expect(item).toBeVisible();
    await identityRolesPage.deleteRole(NEW_ROLE.name);
    await page.reload();
    await expect(item).toBeHidden();
  });
});
