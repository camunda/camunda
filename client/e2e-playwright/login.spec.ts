/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test, expect} from '@playwright/test';

test.describe('login page', () => {
  test('empty page', async ({page}) => {
    await page.goto('/');

    await expect(page).toHaveScreenshot();
  });

  test('field level error', async ({page}) => {
    await page.goto('/');

    await page.getByRole('button', {name: 'Login'}).click();

    await expect(page).toHaveScreenshot();
  });

  test('form level error', async ({page}) => {
    await page.goto('/');

    await page.getByPlaceholder('Username').type('demo');
    await page.getByPlaceholder('Password').type('wrongpassword');
    await page.getByRole('button', {name: 'Login'}).click();

    await expect(page).toHaveScreenshot();
  });
});
