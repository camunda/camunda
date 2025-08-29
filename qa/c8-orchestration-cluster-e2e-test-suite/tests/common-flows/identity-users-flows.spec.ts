/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {relativizePath, Paths} from 'utils/relativizePath';
import {createTestData, createComponentAuthorization} from 'utils/constants';
import {navigateToApp} from '@pages/UtilitiesPage';
import {verifyAccess} from 'utils/accessVerification';
import {sleep} from 'utils/sleep';

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
      await verifyAccess(page, false);
    });

    await test.step(`Verify Tasklist access is denied`, async () => {
      await verifyAccess(page, false, 'tasklist');
    });

    await test.step(`Verify Operate access is denied`, async () => {
      await verifyAccess(page, false, 'operate');
    });
  });

  test('Admin user can remove Authorizations granted to user', async ({
    page,
    loginPage,
    identityUsersPage,
    identityHeader,
    identityAuthorizationsPage,
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

    await test.step(`Grant Authorizations to user for all applications`, async () => {
      await identityAuthorizationsPage.navigateToAuthorizations();
      await expect(page).toHaveURL(relativizePath(Paths.authorizations()));

      const COMPONENT_AUTH = createComponentAuthorization(
        {name: testUser!.name ?? testUser!.username},
        'User',
      );
      await identityAuthorizationsPage.createAuthorization(COMPONENT_AUTH);
    });

    await test.step(`Login with the new user and verify Identity access`, async () => {
      await identityHeader.logout();
      await loginPage.login(testUser!.username, testUser!.password);
      await expect(page).toHaveURL(new RegExp(`identity`));
      await verifyAccess(page);
    });

    await test.step(`Verify Operate access`, async () => {
      await verifyAccess(page, true, 'operate');
    });

    await test.step(`Verify Tasklist access`, async () => {
      await page.goto(`${process.env.CORE_APPLICATION_URL}/tasklist`);
      await loginPage.login(testUser!.username, testUser!.password);
      await expect(page).toHaveURL(new RegExp(`tasklist`));
      await verifyAccess(page, true, 'tasklist');
    });

    await test.step(`Logout, login with demo and delete the created authorization`, async () => {
      await identityAuthorizationsPage.navigateToAuthorizations();
      await loginPage.login('demo', 'demo');
      await expect(page).toHaveURL(relativizePath(Paths.authorizations()));

      await identityAuthorizationsPage.selectResourceTypeTab('Component');
      await identityAuthorizationsPage.clickDeleteAuthorizationButton(
        testUser!.username,
      );

      await expect(
        identityAuthorizationsPage.deleteAuthorizationModal,
      ).toBeVisible();
      await identityAuthorizationsPage.clickDeleteAuthorizationSubButton();
      await expect(
        identityAuthorizationsPage.deleteAuthorizationModal,
      ).toBeHidden();
      await sleep(1500);
    });

    await test.step(`Logout and login with the new user`, async () => {
      await identityHeader.logout();
      await loginPage.login(testUser!.username, testUser!.password);
    });

    await test.step(`Verify Identity access is denied`, async () => {
      await verifyAccess(page, false);
    });

    await test.step(`Verify Tasklist access is denied`, async () => {
      await verifyAccess(page, false, 'tasklist');
    });

    await test.step(`Verify Operate access is denied`, async () => {
      await verifyAccess(page, false, 'operate');
    });
  });
});
