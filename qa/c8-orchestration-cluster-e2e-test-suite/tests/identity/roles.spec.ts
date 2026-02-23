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
import {waitForItemInList} from '../../utils/waitForItemInList';
import {cleanupRoles} from 'utils/rolesCleanup';

const createdRoleIds: string[] = [];

test.describe.serial('roles CRUD', () => {
  let NEW_ROLE: NonNullable<ReturnType<typeof createTestData>['authRole']>;

  test.beforeAll(() => {
    const testData = createTestData({
      authRole: true,
    });
    NEW_ROLE = testData.authRole!;
    createdRoleIds.push(NEW_ROLE.id);
  });

  test.afterAll(async ({request}) => {
    await cleanupRoles(request, createdRoleIds);
  });

  test.beforeEach(async ({page, loginPage, identityHeader}) => {
    await navigateToApp(page, 'admin');
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

  test('tries to create a role with invalid id', async ({
    identityRolesPage,
  }) => {
    await identityRolesPage.clickCreateRoles();
    await identityRolesPage.idField.fill('invalid!!%');
    await expect(identityRolesPage.createRoleModal).toContainText(
      'Please enter a valid role ID',
    );
    await expect(identityRolesPage.idField).toHaveAttribute(
      'data-invalid',
      'true',
    );
  });

  test('creates a role', async ({identityRolesPage}) => {
    await expect(identityRolesPage.roleCell('Admin')).toBeVisible();
    await identityRolesPage.createRole(NEW_ROLE);
  });

  test('deletes a role', async ({page, identityRolesPage}) => {
    const item = identityRolesPage.roleCell(NEW_ROLE.name);
    await identityRolesPage.deleteRole(NEW_ROLE.name);

    await waitForItemInList(page, item, {
      shouldBeVisible: false,
      clickNext: true,
      timeout: 30000,
    });
  });
});

test.describe('Roles functionalities', () => {
  test.beforeEach(async ({page, loginPage, identityGroupsPage}) => {
    await navigateToApp(page, 'admin');
    await loginPage.login('demo', 'demo');
    await expect(identityGroupsPage.groupsList).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test.skip('User inherits permissions through role assignment', async ({
    page,
    identityRolesPage,
    identityAuthorizationsPage,
    identityUsersPage,
    identityHeader,
    loginPage,
  }) => {
    test.slow();
    const testData = createTestData({
      authRole: true,
      user: true,
    });
    const TEST_ROLE = testData.authRole!;
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
        timeout: 80000,
        clickNext: true,
        shouldBeVisible: true,
        onAfterReload: async () => {
          await identityHeader.navigateToUsers();
          await expect(identityUsersPage.usersList).toBeVisible({
            timeout: 30000,
          });
        },
      });
    });

    await test.step('Create authorization for the test user', async () => {
      await identityHeader.navigateToAuthorizations();
      await expect(page).toHaveURL(relativizePath(Paths.authorizations()));
      await identityAuthorizationsPage.createAuthorization({
        ownerType: 'User',
        ownerId: TEST_USER.name,
        resourceType: 'Component',
        resourceId: '*',
        accessPermissions: ['Access'],
      });
    });

    await test.step('Create test role', async () => {
      await identityHeader.navigateToRoles();
      await identityRolesPage.createRole(TEST_ROLE);
      const item = identityRolesPage.roleCell(TEST_ROLE.id);
      await waitForItemInList(page, item, {
        clickNext: true,
        timeout: 60000,
        onAfterReload: async () => {
          await identityHeader.navigateToRoles();
          await expect(identityRolesPage.rolesList).toBeVisible({
            timeout: 60000,
          });
        },
      });
    });

    await test.step('Create authorization for role', async () => {
      await identityAuthorizationsPage.navigateToAuthorizations();
      await identityAuthorizationsPage.createAuthorization({
        ownerType: 'Role',
        ownerId: TEST_ROLE.name,
        resourceType: 'Authorization',
        resourceId: '*',
        accessPermissions: ['Update', 'Read', 'Create', 'Delete'],
      });
    });

    await test.step('Assign test user to role', async () => {
      await identityHeader.navigateToRoles();
      await identityRolesPage.clickRole(TEST_ROLE.id);
      await identityRolesPage.assignUserToRole(TEST_USER.username);
    });

    await test.step(`Logout and login with test user`, async () => {
      await identityHeader.logout();
      await loginPage.login(TEST_USER.username, TEST_USER.password);
      await expect(identityUsersPage.userCell(TEST_USER.email)).toBeVisible();
    });

    await test.step(`Navigate to Roles and assert roles are not retrieved`, async () => {
      await identityHeader.navigateToRoles();
      await expect(identityRolesPage.rolesList).not.toBeVisible({
        timeout: 60000,
      });
    });

    await test.step(`Create role authorization for the test user`, async () => {
      await identityHeader.navigateToAuthorizations();
      await identityAuthorizationsPage.createAuthorization({
        ownerType: 'User',
        ownerId: TEST_USER.name,
        resourceType: 'Role',
        resourceId: '*',
        accessPermissions: ['Create', 'Read', 'Update', 'Delete'],
      });
    });

    await test.step(`Navigate to Roles and assert roles are retrieved`, async () => {
      await identityHeader.navigateToRoles();
      await expect(identityRolesPage.rolesList).toBeVisible({
        timeout: 60000,
      });
    });
  });

  test.skip('As an Admin user I can unassign user from a role', async ({
    page,
    identityRolesPage,
    identityRolesDetailsPage,
    identityAuthorizationsPage,
    identityUsersPage,
    identityHeader,
    loginPage,
  }) => {
    test.slow();
    const testData = createTestData({
      authRole: true,
      user: true,
    });
    const TEST_ROLE = testData.authRole!;
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
        clickNext: true,
        shouldBeVisible: true,
        onAfterReload: async () => {
          await identityHeader.navigateToUsers();
          await expect(identityUsersPage.usersList).toBeVisible({
            timeout: 30000,
          });
        },
      });
    });

    await test.step('Create * permission for applications for Test user', async () => {
      await identityHeader.navigateToAuthorizations();
      await expect(page).toHaveURL(relativizePath(Paths.authorizations()));
      await identityAuthorizationsPage.createAuthorization({
        ownerType: 'User',
        ownerId: TEST_USER.name,
        resourceType: 'Component',
        resourceId: '*',
        accessPermissions: ['Access'],
      });
    });

    await test.step('Create TestRole with Authorizations CRUD permissions', async () => {
      await identityHeader.navigateToRoles();
      await identityRolesPage.createRole(TEST_ROLE);

      const item = identityRolesPage.roleCell(TEST_ROLE.id);
      await waitForItemInList(page, item, {
        clickNext: true,
        timeout: 60000,
        onAfterReload: async () => {
          await identityHeader.navigateToRoles();
          await expect(identityRolesPage.rolesList).toBeVisible({
            timeout: 60000,
          });
        },
      });

      await identityHeader.navigateToAuthorizations();
      await identityAuthorizationsPage.createAuthorization({
        ownerType: 'Role',
        ownerId: TEST_ROLE.name,
        resourceType: 'Authorization',
        resourceId: '*',
        accessPermissions: ['Update', 'Read', 'Create', 'Delete'],
      });
    });

    await test.step('Give TestRole CRUD permissions on Role resource', async () => {
      await identityHeader.navigateToAuthorizations();
      await identityAuthorizationsPage.createAuthorization({
        ownerType: 'Role',
        ownerId: TEST_ROLE.name,
        resourceType: 'Role',
        resourceId: '*',
        accessPermissions: ['Create', 'Delete', 'Read', 'Update'],
      });
    });

    await test.step('Assign Test user to TestRole', async () => {
      await identityHeader.navigateToRoles();
      await identityRolesPage.clickRole(TEST_ROLE.id);
      await identityRolesPage.assignUserToRole(TEST_USER.username);
    });

    await test.step('Verify Test user has access to Roles before unassignment', async () => {
      await identityHeader.logout();
      await loginPage.login(TEST_USER.username, TEST_USER.password);

      await identityHeader.navigateToRoles();

      await waitForItemInList(
        page,
        identityRolesPage.roleCell(TEST_ROLE.name),
        {
          clickNext: true,
          timeout: 60000,
        },
      );

      await identityHeader.logout();
      await loginPage.login('demo', 'demo');
    });

    await test.step('Unassign Test user from TestRole', async () => {
      await identityHeader.navigateToRoles();
      await identityRolesPage.clickRole(TEST_ROLE.id);
      await waitForItemInList(
        page,
        identityRolesDetailsPage.userCell(TEST_USER.username),
      );
      await identityRolesDetailsPage.unassignUserFromRole(TEST_USER.username);
    });

    await test.step('Verify Test user sees empty state on Roles tab after unassignment', async () => {
      await identityHeader.logout();
      await loginPage.login(TEST_USER.username, TEST_USER.password);

      await identityHeader.navigateToRoles();

      await waitForItemInList(page, identityRolesPage.rolesList, {
        shouldBeVisible: false,
        timeout: 60000,
        emptyStateLocator: identityRolesPage.emptyStateLocator,
      });
    });
  });
});
