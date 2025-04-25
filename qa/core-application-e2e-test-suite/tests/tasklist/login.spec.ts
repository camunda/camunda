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

test.beforeEach(async ({page}) => {
  await navigateToApp(page, 'tasklist');
});

test.describe.parallel('Login Tests', () => {
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Basic Login on TaskList', async ({
    taskListLoginPage,
    taskPanelPage,
  }) => {
    await taskListLoginPage.login('demo', 'demo');
    await expect(taskPanelPage.taskListPageBanner).toBeVisible();
  });

  test('have no a11y violations', async ({makeAxeBuilder}) => {
    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });

  test('show error message on login failure', async ({
    taskListLoginPage,
    makeAxeBuilder,
    page,
  }) => {
    await taskListLoginPage.login('demo', 'wrong');
    await expect(page).toHaveURL('/tasklist/login');
    await expect(taskListLoginPage.errorMessage).toContainText(
      'Username and password do not match',
    );

    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });

  test('block form submission with empty fields', async ({
    taskListLoginPage,
    page,
  }) => {
    await taskListLoginPage.loginButton.click();
    await expect(page).toHaveURL('/tasklist/login');

    await taskListLoginPage.usernameInput.fill('demo');
    await taskListLoginPage.loginButton.click();
    await expect(page).toHaveURL('/tasklist/login');

    await taskListLoginPage.usernameInput.fill(' ');
    await taskListLoginPage.passwordInput.fill('demo');
    await taskListLoginPage.loginButton.click();
    await expect(page).toHaveURL('/tasklist/login');
  });

  test('log out redirect', async ({
    taskListLoginPage,
    tasklistHeader,
    page,
  }) => {
    await taskListLoginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist');

    await tasklistHeader.logout();
    await expect(page).toHaveURL('/tasklist/login');
  });

  test('persistency of a session', async ({taskListLoginPage, page}) => {
    await taskListLoginPage.login('demo', 'demo');

    await expect(page).toHaveURL('/tasklist');
    await page.reload();
    await expect(page).toHaveURL('/tasklist');
  });

  test('redirect to the correct URL after login', async ({
    taskListLoginPage,
    tasklistHeader,
    page,
    taskPanelPage,
  }) => {
    await page.goto('tasklist/123');
    await expect(taskPanelPage.taskListPageBanner).toBeVisible({
      timeout: 60000,
    });
    await taskListLoginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist/123');

    await tasklistHeader.logout();

    await page.goto('/tasklist?filter=unassigned');
    await taskListLoginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist?filter=unassigned');

    await tasklistHeader.logout();

    await page.goto('/tasklist/123?filter=unassigned');
    await taskListLoginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist/123?filter=unassigned');
  });
});
