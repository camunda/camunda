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
import {LOGIN_CREDENTIALS} from 'utils/constants';
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

  const TEST_USER = {
    username: 'Test',
    name: 'Test User',
    email: 'test@test.com',
    password: 'test',
  };

  test('Create new Test user', async ({page, identityUsersPage}) => {
    await identityUsersPage.createUser(TEST_USER);

    const item = identityUsersPage.usersList.getByRole('cell', {
      name: TEST_USER.email,
    });

    await waitForItemInList(page, item, {timeout: 60000});

    await expect(
      identityUsersPage.usersList.getByRole('cell', {
        name: TEST_USER.username,
        exact: true,
      }),
    ).toBeVisible();

    await expect(
      identityUsersPage.usersList.getByRole('cell', {
        name: TEST_USER.name,
        exact: true,
      }),
    ).toBeVisible();

    await expect(
      identityUsersPage.usersList.getByRole('cell', {
        name: TEST_USER.email,
        exact: true,
      }),
    ).toBeVisible();
  });
});
