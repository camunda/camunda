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
    await expect(loginPage.passwordInput).toHaveAttribute('type', 'password');

    await loginPage.login('demo', 'wrong-password');
    await expect(page).toHaveURL(`${relativizePath(Paths.login('identity'))}`);

    await expect(loginPage.errorMessage).toContainText(
      "Username and password don't match",
    );

    await expect(page).toHaveURL(`${relativizePath(Paths.login('identity'))}`);
  });

  test('Log in with valid user account', async ({loginPage, page}) => {
    await loginPage.login('demo', 'demo');

    await expect(page).toHaveURL(`${relativizePath(Paths.users())}`);
  });

  test('Log out', async ({loginPage, identityHeader, page}) => {
    await loginPage.login('demo', 'demo');
    await expect(page).toHaveURL(`${relativizePath(Paths.users())}`);
    await identityHeader.logout();
    await expect(page).toHaveURL(
      `${relativizePath(Paths.login('identity'))}?next=/admin/`,
    );
  });

  test('Redirect to initial page after login', async ({loginPage, page}) => {
    await expect(page).toHaveURL(`${relativizePath(Paths.login('identity'))}`);
    await page.goto(`${relativizePath(Paths.users())}`);
    await expect(page).toHaveURL(
      `${relativizePath(Paths.login('identity'))}?next=${Paths.users()}`,
    );

    await loginPage.login('demo', 'demo');

    await expect(page).toHaveURL(relativizePath(Paths.users()));
  });
});
