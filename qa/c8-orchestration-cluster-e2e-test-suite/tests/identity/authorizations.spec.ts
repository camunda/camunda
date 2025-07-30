/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {
  LOGIN_CREDENTIALS,
  createTestData,
  createApplicationAuthorization,
} from 'utils/constants';
import {waitForItemInList} from 'utils/waitForItemInList';
import {relativizePath, Paths} from 'utils/relativizePath';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.describe.serial('authorizations CRUD', () => {
  let NEW_USER: NonNullable<ReturnType<typeof createTestData>['user']>;
  let NEW_AUTH_ROLE: NonNullable<ReturnType<typeof createTestData>['authRole']>;
  let NEW_USER_AUTHORIZATION: NonNullable<
    ReturnType<typeof createTestData>['userAuth']
  >;
  let NEW_APPLICATION_AUTHORIZATION: NonNullable<
    ReturnType<typeof createApplicationAuthorization>
  >;
  test.beforeAll(() => {
    // Create test data once for the entire serial test suite
    const testData = createTestData({
      user: true,
      authRole: true,
      userAuth: true,
      applicationAuth: true,
    });
    NEW_USER = testData.user!;
    NEW_AUTH_ROLE = testData.authRole!;
    NEW_USER_AUTHORIZATION = testData.userAuth!;
    NEW_APPLICATION_AUTHORIZATION = testData.applicationAuth!;
  });

  test.beforeEach(async ({page, loginPage, identityAuthorizationsPage}) => {
    await identityAuthorizationsPage.navigateToAuthorizations();
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );
    await expect(page).toHaveURL(relativizePath(Paths.authorizations()));
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('create user authorization', async ({
    page,
    identityUsersPage,
    identityRolesPage,
    identityRolesDetailsPage,
    identityAuthorizationsPage,
    identityHeader,
    loginPage,
  }) => {
    await test.step(`Create new user`, async () => {
      await identityUsersPage.navigateToUsers();
      await identityUsersPage.createUser(NEW_USER);
    });

    await test.step(`Login as new user and check users list`, async () => {
      await identityHeader.logout();

      await loginPage.login(NEW_USER.username, NEW_USER.password);
      await expect(page).toHaveURL(relativizePath(Paths.forbidden('identity')));
    });

    await test.step(`Login as main user and create role and assign the new user to it`, async () => {
      await identityHeader.logout();

      await loginPage.login(
        LOGIN_CREDENTIALS.username,
        LOGIN_CREDENTIALS.password,
      );
      await expect(page).toHaveURL(relativizePath(Paths.users()));
      await identityHeader.navigateToRoles();
      await expect(page).toHaveURL(relativizePath(Paths.roles()));

      await identityRolesPage.createRole(NEW_AUTH_ROLE);
      await identityRolesPage.clickRole(NEW_AUTH_ROLE.id);

      await identityRolesDetailsPage.assignUser(NEW_USER);

      await identityAuthorizationsPage.navigateToAuthorizations();
      await expect(page).toHaveURL(relativizePath(Paths.authorizations()));
    });

    await test.step(`Assign authorization to new user`, async () => {
      await identityAuthorizationsPage.createAuthorization(
        NEW_USER_AUTHORIZATION,
      );
      await identityHeader.logout();
      await loginPage.login(NEW_USER.username, NEW_USER.password);
      await expect(page).toHaveURL(relativizePath(Paths.forbidden('identity')));
    });
  });

  test('create application authorization', async ({
    identityUsersPage,
    identityAuthorizationsPage,
    identityHeader,
    loginPage,
  }) => {
    await identityAuthorizationsPage.createAuthorization(
      NEW_APPLICATION_AUTHORIZATION,
    );
    await identityHeader.logout();
    await loginPage.login(NEW_USER.username, NEW_USER.password);
    await expect(identityUsersPage.userCell(NEW_USER.email)).toBeVisible();
    await expect(identityUsersPage.userCell('demo@example.com')).toBeVisible();
  });

  test('delete an authorization', async ({
    page,
    identityHeader,
    loginPage,
    identityUsersPage,
    identityAuthorizationsPage,
  }) => {
    await test.step(`Delete Authorization of new user`, async () => {
      await identityAuthorizationsPage.clickDeleteAuthorizationButton(
        NEW_AUTH_ROLE.id,
      );
      await expect(
        identityAuthorizationsPage.deleteAuthorizationModal,
      ).toBeVisible();
      await identityAuthorizationsPage.clickDeleteAuthorizationSubButton();
      await expect(
        identityAuthorizationsPage.deleteAuthorizationModal,
      ).toBeHidden();
      const item = identityAuthorizationsPage.getAuthorizationCell(
        NEW_AUTH_ROLE.id,
      );
      await waitForItemInList(page, item, {shouldBeVisible: false});
    });

    await test.step(`Logout and login with new user and assert authorization`, async () => {
      await identityHeader.logout();
      await loginPage.login(NEW_USER.username, NEW_USER.password);
      await expect(page).toHaveURL(relativizePath(Paths.forbidden('identity')));
      await identityHeader.logout();
    });

    await test.step(`Logout and login with main user and delete created user`, async () => {
      await loginPage.login(
        LOGIN_CREDENTIALS.username,
        LOGIN_CREDENTIALS.password,
      );
      await identityUsersPage.deleteUser(NEW_USER);
      const userItem = identityUsersPage.userCell(NEW_USER.username);
      await waitForItemInList(page, userItem, {shouldBeVisible: false});
    });
  });
});
