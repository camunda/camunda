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


test.describe.parallel('new user flows', () => {
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
  test('Brand new user cannot access any OC cluster apps', async ({
    page,
    loginPage,
    identityUsersPage,
    identityHeader
  }) => {
    const testUser = {
      username: 'demo1',
      name: 'demo1demo1',
      email: 'demo1@example.com',
      password: 'demo1',
    };
    await identityUsersPage.createUser(testUser);
    await identityHeader.logout();

    await loginPage.login(testUser.username, testUser.password);
    const ocApps = ['identity', 'operate', 'tasklist', 'console'];
    for (const app of ocApps) {
      await page.goto(relativizePath(`/${app}`));
      await expect(page.locator('body')).toContainText(
        /403|forbidden|unauthorized/i,
      );
    }
  });
});
