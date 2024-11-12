/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '@/test-fixtures';

test.beforeEach(async ({loginPage}) => {
  await loginPage.goto();
});

test.describe.parallel('login page', () => {
  test('redirect to the main page on login', async ({loginPage, page}) => {
    expect(await loginPage.passwordInput.getAttribute('type')).toEqual(
      'password',
    );

    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });

    await expect(page).toHaveURL('/tasklist');
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
    await loginPage.login({
      username: 'demo',
      password: 'wrong',
    });

    await expect(page).toHaveURL('/tasklist/login');
    await expect(loginPage.errorMessage).toContainText(
      'Credentials could not be verified',
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

  test('log out redirect', async ({loginPage, header, page}) => {
    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });
    await expect(page).toHaveURL('/tasklist');

    await header.logout();
    await expect(page).toHaveURL('/tasklist/login');
  });

  test('persistency of a session', async ({loginPage, page}) => {
    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });

    await expect(page).toHaveURL('/tasklist');
    await page.reload();
    await expect(page).toHaveURL('/tasklist');
  });

  test('redirect to the correct URL after login', async ({
    loginPage,
    header,
    page,
  }) => {
    await page.goto('/tasklist/123');
    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });
    await expect(page).toHaveURL('/tasklist/123');

    await header.logout();

    await page.goto('/tasklist?filter=unassigned');
    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });
    await expect(page).toHaveURL('/tasklist?filter=unassigned');

    await header.logout();

    await page.goto('/tasklist/123?filter=unassigned');
    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });
    await expect(page).toHaveURL('/tasklist/123?filter=unassigned');
  });
});
