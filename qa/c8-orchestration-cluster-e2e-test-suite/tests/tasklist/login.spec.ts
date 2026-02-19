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

test.describe.parallel('Login Tests', () => {
  test.beforeEach(async ({page}) => {
    await navigateToApp(page, 'tasklist');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Basic Login on TaskList', async ({loginPage, taskPanelPage}) => {
    await loginPage.login('demo', 'demo');
    await expect(taskPanelPage.taskListPageBanner).toBeVisible();
  });

  test('have no a11y violations', async ({makeAxeBuilder}) => {
    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });

  test('show error message on login failure', async ({
    loginPage,
    makeAxeBuilder,
    page,
  }) => {
    await loginPage.login('demo', 'wrong');
    await expect(page).toHaveURL('/tasklist/login');
    await expect(loginPage.errorMessage).toContainText(
      'Username and password do not match',
    );

    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });

  test('block form submission with empty fields', async ({loginPage, page}) => {
    await loginPage.loginButton.click();
    await expect(page).toHaveURL('/tasklist/login');

    await loginPage.usernameInput.fill('demo');
    await loginPage.loginButton.click();
    await expect(page).toHaveURL('/tasklist/login');

    await loginPage.usernameInput.fill(' ');
    await loginPage.passwordInput.fill('demo');
    await loginPage.loginButton.click();
    await expect(page).toHaveURL('/tasklist/login');
  });

  test('log out redirect', async ({loginPage, tasklistHeader, page}) => {
    await loginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist');

    await tasklistHeader.logout();
    await expect(page).toHaveURL('/tasklist/login');
  });

  test('persistency of a session', async ({loginPage, page}) => {
    await loginPage.login('demo', 'demo');

    await expect(page).toHaveURL('/tasklist');
    await page.reload();
    await expect(page).toHaveURL('/tasklist');
  });

  test('redirect to the correct URL after login', async ({
    loginPage,
    tasklistHeader,
    page,
  }) => {
    await page.goto('tasklist/123');

    await loginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist/123');

    await page.goto('/tasklist?filter=unassigned');
    await tasklistHeader.logout();

    await loginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist?filter=unassigned');

    await page.goto('/tasklist/123?filter=unassigned');
    await tasklistHeader.logout();

    await loginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist/123?filter=unassigned');
  });
});
