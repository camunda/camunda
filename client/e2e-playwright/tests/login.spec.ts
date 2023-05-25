/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test, expect} from '@playwright/test';
import {test as axeTest} from '../axe-test';

test.describe('login page', () => {
  test('redirect to the main page on login', async ({page}) => {
    await page.goto('/login');

    expect(await page.getByLabel('Password').getAttribute('type')).toEqual(
      'password',
    );

    await page.getByPlaceholder('Username').fill('demo');
    await page.getByPlaceholder('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();

    await expect(page).toHaveURL('/');
  });

  axeTest('have no a11y violations', async ({page, makeAxeBuilder}) => {
    await page.goto('/login');

    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });
});
