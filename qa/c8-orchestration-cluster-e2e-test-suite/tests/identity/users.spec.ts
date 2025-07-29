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
  await navigateToApp(page, 'identity');
  await loginPage.login(LOGIN_CREDENTIALS.username, LOGIN_CREDENTIALS.password);
  await expect(page).toHaveURL(relativizePath(Paths.users()));
});

test.describe.serial('users CRUD', () => {
  let NEW_USER: NonNullable<ReturnType<typeof createTestData>['user']>;
  let EDITED_USER: typeof NEW_USER;

  test.beforeAll(() => {
    const testData = createTestData({
      user: true,
    });
    NEW_USER = testData.user!;
    EDITED_USER = {
      ...NEW_USER,
      name: `Edited ${NEW_USER.name}`,
      email: `edited.${NEW_USER.email}`,
      password: `edited${NEW_USER.password}`,
    };
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('create a user', async ({identityUsersPage}) => {
    await expect(identityUsersPage.userCell('demo@example.com')).toBeVisible();
    await identityUsersPage.createUser(NEW_USER);
    await expect(identityUsersPage.userCell(NEW_USER.email)).toBeVisible();
  });

  test('edit a user', async ({identityUsersPage, page}) => {
    await expect(identityUsersPage.userCell(NEW_USER.email)).toBeVisible();
    await identityUsersPage.editUser(NEW_USER, EDITED_USER);
    const item = identityUsersPage.userCell(EDITED_USER.email);
    await waitForItemInList(page, item);
  });

  test('delete a user', async ({identityUsersPage}) => {
    await expect(identityUsersPage.userCell(EDITED_USER.name)).toBeVisible();
    await identityUsersPage.deleteUser(EDITED_USER);
    await expect(identityUsersPage.userCell(EDITED_USER.name)).toBeHidden({
      timeout: 60000,
    });
  });
});
