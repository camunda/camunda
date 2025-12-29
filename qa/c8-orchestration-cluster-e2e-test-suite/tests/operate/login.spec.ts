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

test.describe.parallel('Login Tests', () => {
  test.beforeEach(async ({page}) => {
    await navigateToApp(page, 'operate');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Log in with invalid user account', async ({page, loginPage}) => {
    await expect(loginPage.passwordInput).toHaveAttribute('type', 'password');

    await loginPage.login('demo', 'wrong-password');
    await expect(loginPage.invalidCredentialsError).toBeVisible();
    await expect(page).toHaveURL(`${relativizePath(Paths.login('operate'))}`);
  });

  test('Log in with valid user account', async ({
    page,
    loginPage,
    operateHomePage,
  }) => {
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await expect(page).toHaveURL(`${relativizePath(Paths.operateDashboard())}`);
  });

  test('Log out', async ({page, loginPage, operateHomePage}) => {
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await expect(page).toHaveURL(`${relativizePath(Paths.operateDashboard())}`);

    await operateHomePage.logout();
    await expect(page).toHaveURL(`${relativizePath(Paths.login('operate'))}`);
  });

  test('Redirect to initial page after login', async ({page, loginPage}) => {
    await expect(page).toHaveURL(`${relativizePath(Paths.login('operate'))}`);
    await page.goto(
      `${relativizePath(Paths.operateProcesses('active=true&incidents=true'))}`,
    );
    await expect(page).toHaveURL(`${relativizePath(Paths.login('operate'))}`);

    await loginPage.login('demo', 'demo');
    await expect(page).toHaveURL(
      `${relativizePath(Paths.operateProcesses('active=true&incidents=true'))}`,
    );
  });
});
