/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {navigateToApp} from '@pages/UtilitiesPage';
import {expect} from '@playwright/test';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {relativizePath, Paths} from 'utils/relativizePath';
import {createTestData} from '../../utils/constants';
import {sleep} from '../../utils/sleep';

test.describe('Users Page Tests', () => {
  test.beforeEach(async ({loginPage, page}) => {
    await navigateToApp(page, 'identity');
    await loginPage.login('demo', 'demo');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Admin user can delete user', async ({
    page,
    loginPage,
    identityUsersPage,
    identityHeader,
  }) => {
    const testData = createTestData({user: true});
    const testUser = testData.user!;
    await identityUsersPage.createUser({
      username: testUser.username,
      password: testUser.password,
      email: testUser.email!,
      name: testUser.name,
    });

    await expect(identityUsersPage.usersList).toBeVisible();
    await expect(identityUsersPage.usersList).toContainText(testUser.username);
    await identityUsersPage.deleteUser(testUser);
    await identityHeader.logout();
    await sleep(2000);
    await expect(page).toHaveURL(
      `${relativizePath(Paths.login('identity'))}?next=/identity/`,
    );

    await test.step(`Deleted user cannot access Identity`, async () => {
      await navigateToApp(page, `identity`);
      await loginPage.login(testUser.username, testUser.password);
      await expect(page).toHaveURL(new RegExp(`identity`));
      await expect(loginPage.errorMessage).toContainText(
        /Username and [Pp]assword do(?: not|n't) match/,
      );
    });

    await test.step(`Deleted user cannot access Tasklist`, async () => {
      await navigateToApp(page, `tasklist`);
      await loginPage.login(testUser.username, testUser.password);
      await expect(page).toHaveURL(new RegExp(`tasklist`));
      await expect(loginPage.errorMessage).toContainText(
        /Username and [Pp]assword do(?: not|n't) match/,
      );
    });

    await test.step(`Deleted user cannot access Operate`, async () => {
      await navigateToApp(page, `operate`);
      await loginPage.login(testUser.username, testUser.password);
      await expect(page).toHaveURL(new RegExp(`operate`));
      await expect(loginPage.errorMessage).toContainText(
        /Username and [Pp]assword do(?: not|n't) match/,
      );
    });
  });
});
