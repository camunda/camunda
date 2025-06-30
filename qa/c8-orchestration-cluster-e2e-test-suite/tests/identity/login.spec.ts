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

test.describe.parallel('login page', () => {
  test.beforeEach(async ({page}) => {
    await navigateToApp(page, 'identity');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Log in with invalid user account', async ({loginPage, page}) => {
    expect(await loginPage.passwordInput.getAttribute('type')).toEqual(
      'password',
    );

    await loginPage.login('demo', 'wrong-password');
    await expect(page).toHaveURL('/identity/login');

    await expect(loginPage.errorMessage).toContainText(
      'Username and password do not match',
    );

    await expect(page).toHaveURL('/identity/login');
  });

  test('Log in with valid user account', async ({loginPage, page}) => {
    await loginPage.login('demo', 'demo');

    await expect(page).toHaveURL('/identity/users');
  });

  test('Log out', async ({loginPage, identityHeader, page}) => {
    await loginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/identity/users');
    await identityHeader.logout();
    await expect(page).toHaveURL('identity/login?next=/identity');
  });

  test('Redirect to initial page after login', async ({loginPage, page}) => {
    await expect(page).toHaveURL('/identity/login');
    await page.goto(relativizePath(Paths.users()));
    await expect(page).toHaveURL(
      `${relativizePath(Paths.login())}?next=/identity${Paths.users()}`,
    );

    await loginPage.login('demo', 'demo');

    await expect(page).toHaveURL(relativizePath(Paths.users()));
  });
});
