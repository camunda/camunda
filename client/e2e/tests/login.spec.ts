/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';

test.beforeEach(async ({testSetupPage}) => {
  await testSetupPage.goToLoginPage();
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

    await expect(page).toHaveURL('/');
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

    await expect(page).toHaveURL('/login');
    await expect(loginPage.errorMessage).toContainText(
      'Username and password do not match',
    );

    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });

  test('block form submission with empty fields', async ({loginPage, page}) => {
    await loginPage.clickLoginButton();
    await expect(page).toHaveURL('/login');

    await loginPage.fillUsername('demo');
    await loginPage.clickLoginButton();
    await expect(page).toHaveURL('/login');

    await loginPage.fillUsername(' ');
    await loginPage.fillPassword('demo');
    await loginPage.clickLoginButton();
    await expect(page).toHaveURL('/login');
  });

  test('log out redirect', async ({loginPage, mainPage, page}) => {
    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });
    await expect(page).toHaveURL('/');

    await mainPage.logout();
    await expect(page).toHaveURL('/login');
  });

  test('persistency of a session', async ({loginPage, page}) => {
    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });

    await expect(page).toHaveURL('/');
    await page.reload();
    await expect(page).toHaveURL('/');
  });

  test('redirect to the correct URL after login', async ({
    loginPage,
    mainPage,
    page,
  }) => {
    await loginPage.navigateToURL('/123');
    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });
    await expect(page).toHaveURL('/123');

    await mainPage.logout();

    await loginPage.navigateToURL('/?filter=unassigned');
    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });
    await expect(page).toHaveURL('/?filter=unassigned');

    await mainPage.logout();

    await loginPage.navigateToURL('/123?filter=unassigned');
    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });
    await expect(page).toHaveURL('/123?filter=unassigned');
  });
});
