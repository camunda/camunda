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
import {createTestData} from 'utils/constants';
import {navigateToApp} from '@pages/UtilitiesPage';
import {verifyAccess} from 'utils/accessVerification';
import {waitForItemInList} from 'utils/waitForItemInList';
import {createInstances, deploy} from 'utils/zeebeClient';
import {sleep} from 'utils/sleep';
import {cleanupUsers} from 'utils/usersCleanup';

test.describe('Identity User Flows', () => {
  test.beforeAll(async () => {
    await deploy(['./resources/simpleProcessForIdentity.bpmn']);
    await sleep(500);
    await createInstances('identityProcess', 1, 1);
  });

  const createdUsernames: string[] = [];

  test.beforeEach(async ({loginPage, page}) => {
    await navigateToApp(page, 'admin');
    await loginPage.login('demo', 'demo');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test.afterAll(async ({request}) => {
    await cleanupUsers(request, createdUsernames);
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
      `${relativizePath(Paths.login('identity'))}?next=/admin/`,
    );

    await test.step(`Deleted user cannot access Identity`, async () => {
      await navigateToApp(page, `identity`);
      await loginPage.login(testUser.username, testUser.password);
      await expect(page).toHaveURL(new RegExp(`admin`));
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

      createdUsernames.push(testUser.username);
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

  test('Admin user can grant and revoke component authorization for user', async ({
    page,
    loginPage,
    identityUsersPage,
    identityHeader,
    identityAuthorizationsPage,
  }) => {
    test.slow();
    let testUser: {
      username: string;
      name: string;
      email: string;
      password: string;
    };

    await test.step(`Create new user`, async () => {
      const testData = createTestData({user: true});
      testUser = testData.user!;

      await identityUsersPage.createUser({
        username: testUser.username,
        password: testUser.password,
        email: testUser.email,
        name: testUser.name,
      });

      createdUsernames.push(testUser.username);
    });

    await test.step(`Grant Authorizations to user for all applications`, async () => {
      await identityAuthorizationsPage.navigateToAuthorizations();
      await expect(page).toHaveURL(relativizePath(Paths.authorizations()));

      await identityAuthorizationsPage.createAuthorization({
        ownerType: 'User',
        ownerId: testUser.name,
        resourceType: 'Component',
        resourceId: '*',
        accessPermissions: ['Access'],
      });

      const authorizationItem = identityAuthorizationsPage.getAuthorizationCell(
        testUser!.username,
      );
      await waitForItemInList(page, authorizationItem, {
        shouldBeVisible: true,
        timeout: 10000,
        onAfterReload: () =>
          identityAuthorizationsPage.selectResourceTypeTab('Component'),
        clickNext: true,
      });
    });

    await test.step(`Login with the new user and verify Identity access`, async () => {
      await identityHeader.logout();
      await loginPage.login(testUser!.username, testUser!.password);
      await expect(page).toHaveURL(new RegExp(`admin`));
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

      const authorizationItem = identityAuthorizationsPage.getAuthorizationCell(
        testUser!.username,
      );
      await waitForItemInList(page, authorizationItem, {
        shouldBeVisible: false,
        timeout: 15000,
        onAfterReload: () =>
          identityAuthorizationsPage.selectResourceTypeTab('Component'),
      });
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

  test('New user can inherit permissions when assigned to a group', async ({
    page,
    identityGroupsPage,
    identityAuthorizationsPage,
    identityUsersPage,
    identityHeader,
    loginPage,
    operateHomePage,
    tasklistHeader,
  }) => {
    const testData = createTestData({
      group: true,
      user: true,
    });
    const TEST_GROUP = testData.group!;
    const TEST_USER = testData.user!;

    await test.step('Create test user', async () => {
      await identityUsersPage.createUser({
        username: TEST_USER.username,
        password: TEST_USER.password,
        email: TEST_USER.email!,
        name: TEST_USER.name ?? TEST_USER.username,
      });

      const userName = identityUsersPage.userCell(TEST_USER.username);
      await waitForItemInList(page, userName, {
        timeout: 60000,
        clickNext: true,
      });
    });

    await test.step('Create test group', async () => {
      await identityGroupsPage.navigateToGroups();
      await identityGroupsPage.createGroup(
        TEST_GROUP.groupId,
        TEST_GROUP.name,
        TEST_GROUP.description,
      );
      const item = identityGroupsPage.groupCell(TEST_GROUP.groupId);
      await waitForItemInList(page, item, {
        clickNext: true,
        timeout: 60000,
        onAfterReload: async () => {
          await identityGroupsPage.navigateToGroups();
          await expect(identityGroupsPage.groupsList).toBeVisible({
            timeout: 60000,
          });
        },
      });
    });

    await test.step('Create authorization for the test group', async () => {
      await identityHeader.navigateToAuthorizations();
      await expect(page).toHaveURL(relativizePath(Paths.authorizations()));
      await identityAuthorizationsPage.createAuthorization({
        ownerType: 'Group',
        ownerId: TEST_GROUP.name,
        resourceType: 'Component',
        resourceId: '*',
        accessPermissions: ['Access'],
      });
    });

    await test.step('Assign test user to group', async () => {
      await identityGroupsPage.navigateToGroups();
      await identityGroupsPage.clickGroupId(TEST_GROUP.groupId);
      await identityGroupsPage.assignUserToGroup(
        TEST_USER.username,
        TEST_USER.email!,
      );
      await page.reload();
    });

    await test.step('Verify authorization was created', async () => {
      await identityHeader.navigateToAuthorizations();
      await identityAuthorizationsPage.clickResourceType('Component');

      const authorizationItem = identityAuthorizationsPage.getAuthorizationCell(
        TEST_GROUP.groupId,
      );
      await waitForItemInList(page, authorizationItem, {
        shouldBeVisible: true,
        timeout: 10000,
        onAfterReload: () =>
          identityAuthorizationsPage.selectResourceTypeTab('Component'),
        clickNext: true,
      });
    });

    await test.step('Verify test user can access Identity', async () => {
      await identityHeader.logout();
      await loginPage.login(TEST_USER.username, TEST_USER.password);
      await expect(page).toHaveURL(new RegExp('admin'), {timeout: 10000});
      await sleep(2000);
      await verifyAccess(page, true);
    });

    await test.step('Verify test user can access Operate but cannot view process instances', async () => {
      await page.goto(`${process.env.CORE_APPLICATION_URL}/operate`);
      await operateHomePage.clickProcessesTab();
      await expect(page.getByText('identityProcess')).not.toBeVisible({
        timeout: 30000,
      });
    });

    await test.step('Verify test user can access Tasklist but cannot view the userTask', async () => {
      await page.goto(`${process.env.CORE_APPLICATION_URL}/tasklist`);
      console.log('sero ' + page.url());
      await expect(page).toHaveURL(new RegExp(`tasklist`));
      await verifyAccess(page, true);
      await expect(page.getByText('identityProcess')).not.toBeVisible({
        timeout: 30000,
      });
      await tasklistHeader.logout();
    });

    await test.step('Login with demo user and create authorization for the group', async () => {
      await navigateToApp(page, 'admin');
      await loginPage.login('demo', 'demo');
      await identityHeader.navigateToAuthorizations();
      await identityAuthorizationsPage.createAuthorization({
        ownerType: 'Group',
        ownerId: TEST_GROUP.name,
        resourceType: 'Authorization',
        resourceId: '*',
        accessPermissions: ['Read'],
      });
    });

    await test.step('Verify authorization was created', async () => {
      await identityAuthorizationsPage.clickResourceType('Authorization');
      await identityAuthorizationsPage.assertAuthorizationExists(
        TEST_GROUP.groupId,
        'Group',
        ['Read'],
        'Authorization',
      );
    });

    await test.step('Verify test user can view process instances in Operate', async () => {
      await page.goto(`${process.env.CORE_APPLICATION_URL}/operate`);
      await operateHomePage.clickProcessesTab();
      await expect(page.getByText('identityProcess').first()).toBeVisible({
        timeout: 30000,
      });
    });

    await test.step('Verify test user can view the userTask in Tasklist', async () => {
      await page.goto(`${process.env.CORE_APPLICATION_URL}/tasklist`);
      await expect(page).toHaveURL(new RegExp(`tasklist`));
      await expect(page.getByText('identityProcess').first()).toBeVisible({
        timeout: 30000,
      });
    });
  });
});
