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
import {createTestData, LOGIN_CREDENTIALS} from 'utils/constants';
import {waitForItemInList} from 'utils/waitForItemInList';

test.describe.parallel('login page', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await navigateToApp(page, 'identity');
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );
    await expect(page).toHaveURL(relativizePath(Paths.users()));
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Create new Test user', async ({page, identityUsersPage}) => {
    const testData = createTestData({user: true});
    const testUser = testData.user!;

    await identityUsersPage.createUser({
      username: testUser.username,
      password: testUser.password,
      email: testUser.email!,
      name: testUser.name,
    });
    const item = identityUsersPage.usersList.getByRole('cell', {
      name: testUser.email,
    });

    await waitForItemInList(page, item, {timeout: 60000});

    await expect(
      identityUsersPage.usersList.getByRole('cell', {
        name: testUser.username,
        exact: true,
      }),
    ).toBeVisible();

    await expect(
      identityUsersPage.usersList.getByRole('cell', {
        name: testUser.name,
        exact: true,
      }),
    ).toBeVisible();

    await expect(
      identityUsersPage.usersList.getByRole('cell', {
        name: testUser.email,
        exact: true,
      }),
    ).toBeVisible();
  });
});
