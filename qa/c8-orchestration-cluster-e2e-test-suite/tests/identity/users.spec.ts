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
import {waitForItemInList} from 'utils/waitForItemInList';
import {navigateToApp} from '@pages/UtilitiesPage';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.beforeEach(async ({page, loginPage}) => {
  await navigateToApp(page, 'admin');
  await loginPage.login(LOGIN_CREDENTIALS.username, LOGIN_CREDENTIALS.password);
  await expect(page).toHaveURL(relativizePath(Paths.users()));
});

test.describe.serial('users CRUD', () => {
  let NEW_USER: NonNullable<ReturnType<typeof createTestData>['user']>;
  let NEW_MINIMUM_USER: typeof NEW_USER;
  let EDITED_USER: typeof NEW_USER;
  let EDITED_MINIMUM_USER: typeof NEW_USER;

  test.beforeAll(() => {
    const testData = createTestData({
      user: true,
    });
    NEW_USER = testData.user!;
    NEW_MINIMUM_USER = {
      ...NEW_USER,
      username: `minimum${NEW_USER.username}`,
      name: '',
      email: '',
    };
    EDITED_USER = {
      ...NEW_USER,
      name: `Edited ${NEW_USER.name}`,
      email: `edited.${NEW_USER.email}`,
      password: `edited${NEW_USER.password}`,
    };
    EDITED_MINIMUM_USER = {
      ...EDITED_USER,
      name: '',
      email: '',
    };
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('tries to create a mapping rule with invalid id', async ({
    identityUsersPage,
  }) => {
    await identityUsersPage.createUserButton.click();
    await identityUsersPage.createUsernameField.fill('invalid!!%');
    await expect(identityUsersPage.createUserModal).toContainText(
      'Please enter a valid username',
    );
    await expect(identityUsersPage.createUsernameField).toHaveAttribute(
      'data-invalid',
      'true',
    );
  });

  test('create a user', async ({identityUsersPage, page}) => {
    await expect(identityUsersPage.userCell('demo@example.com')).toBeVisible();
    await identityUsersPage.createUser(NEW_USER);
    await waitForItemInList(page, identityUsersPage.userCell(NEW_USER.email), {
      clickNext: true,
      timeout: 30000,
    });
  });

  test('edit a user', async ({identityUsersPage, page}) => {
    await identityUsersPage.editUser(NEW_USER, EDITED_USER);
    const item = identityUsersPage.userCell(EDITED_USER.email);
    await waitForItemInList(page, item, {
      clickNext: true,
      timeout: 30000,
    });
  });

  test('edit a user with minimum properties', async ({
    identityUsersPage,
    page,
  }) => {
    await identityUsersPage.editUser(EDITED_USER, EDITED_MINIMUM_USER);
    const item = identityUsersPage.userCell(EDITED_MINIMUM_USER.username);
    await waitForItemInList(page, item, {
      clickNext: true,
      timeout: 30000,
    });
  });

  test('delete a user', async ({identityUsersPage, page}) => {
    const item = identityUsersPage.userCell(EDITED_USER.email);
    await identityUsersPage.deleteUser(EDITED_USER);

    await waitForItemInList(page, item, {
      shouldBeVisible: false,
      clickNext: true,
      timeout: 30000,
    });
  });

  test('create a user with minimum properties', async ({
    identityUsersPage,
    page,
  }) => {
    await expect(identityUsersPage.userCell('demo@example.com')).toBeVisible();
    await identityUsersPage.createUser(NEW_MINIMUM_USER);
    await waitForItemInList(
      page,
      identityUsersPage.userCell(NEW_MINIMUM_USER.username),
      {
        clickNext: true,
        timeout: 30000,
      },
    );
  });
});
