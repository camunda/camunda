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
import {createTestData} from 'utils/constants';

test.describe('Identity User Flows', () => {
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
    await expect(page).toHaveURL(
      `${relativizePath(Paths.login('identity'))}?next=/identity/`,
    );

    await test.step(`Deleted user cannot access Identity`, async () => {
      await navigateToApp(page, `identity`);
      await loginPage.login(testUser.username, testUser.password);
      await expect(page).toHaveURL(new RegExp(`identity`));
      await loginPage.expectInvalidCredentialsError();
    });

    await test.step(`Deleted user cannot access Tasklist`, async () => {
      await navigateToApp(page, `tasklist`);
      await loginPage.login(testUser.username, testUser.password);
      await expect(page).toHaveURL(new RegExp(`tasklist`));
      await loginPage.expectInvalidCredentialsError();
    });

    await test.step(`Deleted user cannot access Operate`, async () => {
      await navigateToApp(page, `operate`);
      await loginPage.login(testUser.username, testUser.password);
      await expect(page).toHaveURL(new RegExp(`operate`));
      await loginPage.expectInvalidCredentialsError();
    });
  });

  // eslint-disable-next-line playwright/expect-expect
  test('Brand new user cannot access any OC cluster apps', async ({
    page,
    loginPage,
    identityUsersPage,
    identityHeader,
    accessDeniedPage,
  }) => {
    let testUser:
      | {username: string; password: string; email?: string; name?: string}
      | undefined;

    await test.step(`Create new user`, async () => {
      const testData = createTestData({user: true});
      testUser = testData.user!;

      await identityUsersPage.createUser({
        username: testUser.username,
        password: testUser.password,
        email: testUser.email!,
        name: testUser.name ?? testUser.username,
      });
    });

    await test.step(`Login with the new user and verify Identity access is denied`, async () => {
      await identityHeader.logout();
      await loginPage.login(testUser!.username, testUser!.password);

      await accessDeniedPage.expectAccessDenied();
    });

    await test.step(`Verify Operate access is denied`, async () => {
      await page.goto(relativizePath(`/operate`));
      await accessDeniedPage.expectAccessDenied();
    });

    await test.step(`Verify Tasklist access is denied`, async () => {
      await page.goto(relativizePath(`/tasklist`));
      await accessDeniedPage.expectAccessDenied();
    });
  });
});
