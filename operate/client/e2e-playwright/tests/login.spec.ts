/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {loginTest as test} from '../test-fixtures';
import {Paths} from 'modules/Routes';

test.beforeEach(async ({loginPage}) => {
  await loginPage.navigateToLogin();
});

test.describe('login page', () => {
  test('Log in with invalid user account', async ({loginPage, page}) => {
    expect(await loginPage.passwordInput.getAttribute('type')).toEqual(
      'password',
    );

    await loginPage.login({
      username: 'demo',
      password: 'wrong-password',
    });

    await expect(
      page.getByRole('alert').getByText('Username and password do not match'),
    ).toBeVisible();
    await expect(page).toHaveURL(Paths.login());
  });

  test('Log in with valid user account', async ({loginPage, page}) => {
    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });

    await expect(page).toHaveURL(Paths.dashboard());
  });

  test('Log out', async ({loginPage, commonPage, page}) => {
    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });

    await expect(page).toHaveURL('');
    await commonPage.logout();
    await expect(page).toHaveURL(Paths.login());
  });

  test('Redirect to initial page after login', async ({loginPage, page}) => {
    await expect(page).toHaveURL(Paths.login());
    await page.goto(`${Paths.processes()}?active=true&incidents=true`);
    await expect(page).toHaveURL(Paths.login());

    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });

    await expect(page).toHaveURL(
      `${Paths.processes()}?active=true&incidents=true`,
    );
  });
});
