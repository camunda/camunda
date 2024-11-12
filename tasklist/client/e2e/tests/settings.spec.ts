/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '@/test-fixtures';

test.beforeEach(async ({page, loginPage}) => {
  await loginPage.goto();
  await loginPage.login({
    username: 'demo',
    password: 'demo',
  });
  await expect(page).toHaveURL('/tasklist');
});

test.describe('settings', () => {
  test('change language', async ({page, header}) => {
    await header.changeLanguage('Français');

    await expect(
      page.getByRole('heading', {name: 'Bienvenue dans Tasklist'}),
    ).toBeVisible();
    await expect(
      page.getByRole('heading', {name: 'Tâches ouvertes'}),
    ).toBeVisible();
    await expect(page.getByRole('button', {name: 'Déconnexion'})).toBeVisible();
  });
});
