/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
    await expect(page).toHaveURL(`.${Paths.login()}`);
  });

  test('Log in with valid user account', async ({loginPage, page}) => {
    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });

    await expect(page).toHaveURL('../operate'); // dashboard url, we need to do this because baseURL contains a slash at the end as expected by playwright
  });

  test('Log out', async ({loginPage, commonPage, page}) => {
    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });

    await expect(page).toHaveURL('../operate'); // dashboard url, we need to do this because baseURL contains a slash at the end as expected by playwright
    await commonPage.logout();
    await expect(page).toHaveURL(`.${Paths.login()}`);
  });

  test('Redirect to initial page after login', async ({loginPage, page}) => {
    await expect(page).toHaveURL(`.${Paths.login()}`);
    await page.goto(`.${Paths.processes()}?active=true&incidents=true`);
    await expect(page).toHaveURL(`.${Paths.login()}`);

    await loginPage.login({
      username: 'demo',
      password: 'demo',
    });

    await expect(page).toHaveURL(
      `.${Paths.processes()}?active=true&incidents=true`,
    );
  });
});
