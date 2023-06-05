/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test, expect} from '@playwright/test';
import {test as axeTest} from '../axe-test';

test.beforeEach(async ({page}) => {
  await page.goto('/login');
});

test.describe.parallel('login page', () => {
  test('redirect to the main page on login', async ({page}) => {
    expect(await page.getByLabel('Password').getAttribute('type')).toEqual(
      'password',
    );

    await page.getByPlaceholder('Username').fill('demo');
    await page.getByPlaceholder('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();

    await expect(page).toHaveURL('/');
  });

  axeTest('have no a11y violations', async ({page, makeAxeBuilder}) => {
    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });

  axeTest(
    'show error message on login failure',
    async ({page, makeAxeBuilder}) => {
      await page.getByPlaceholder('Username').fill('demo');
      await page.getByPlaceholder('Password').fill('wrong');
      await page.getByRole('button', {name: 'Login'}).click();
      await expect(page).toHaveURL('/login');

      expect(
        page
          .getByRole('alert', {
            name: 'Username and password do not match',
          })
          .isVisible(),
      ).toBeTruthy();

      const results = await makeAxeBuilder().analyze();

      expect(results.violations).toHaveLength(0);
      expect(results.passes.length).toBeGreaterThan(0);
    },
  );

  test('block form submission with empty fields', async ({page}) => {
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/login');
    await page.getByPlaceholder('Username').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/login');
    await page.getByPlaceholder('Username').fill(' ');
    await page.getByPlaceholder('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/login');
  });

  test('log out redirect', async ({page}) => {
    await page.getByPlaceholder('Username').fill('demo');
    await page.getByPlaceholder('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/');
    await page.getByRole('button', {name: 'Open Settings'}).click();
    await page.getByRole('button', {name: 'Log out'}).click();
    await expect(page).toHaveURL('/login');
  });

  test('persistency of a session', async ({page}) => {
    await page.getByPlaceholder('Username').fill('demo');
    await page.getByPlaceholder('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/');
    await page.reload();
    await expect(page).toHaveURL('/');
  });

  test('redirect to the correct URL after login', async ({page}) => {
    await page.goto('/123');
    await page.getByPlaceholder('Username').fill('demo');
    await page.getByPlaceholder('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/123');
    await page.getByRole('button', {name: 'Open Settings'}).click();
    await page.getByRole('button', {name: 'Log out'}).click();

    await page.goto('/?filter=unassigned');
    await page.getByPlaceholder('Username').fill('demo');
    await page.getByPlaceholder('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/?filter=unassigned');
    await page.getByRole('button', {name: 'Open Settings'}).click();
    await page.getByRole('button', {name: 'Log out'}).click();

    await page.goto('/123?filter=unassigned');
    await page.getByPlaceholder('Username').fill('demo');
    await page.getByPlaceholder('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/123?filter=unassigned');
    await page.getByRole('button', {name: 'Open Settings'}).click();
    await page.getByRole('button', {name: 'Log out'}).click();
  });
});
