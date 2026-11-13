/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {publicTest as test} from 'fixtures';
import {navigateToApp} from '@pages/UtilitiesPage';
import {captureFailureVideo, captureScreenshot} from '@setup';

test.describe('settings', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await navigateToApp(page, 'tasklist');
    await loginPage.login('demo', 'demo');
    await expect(page).toHaveURL('/tasklist');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('change language', async ({page, tasklistHeader}) => {
    await tasklistHeader.changeLanguage('Français');
    await expect(
      page.getByRole('heading', {name: 'Bienvenue dans Tasklist'}),
    ).toBeVisible();
    // The active filter is plain text inside #filter-select; the trigger's
    // accessible name is the static "Filtres".
    await expect(
      page.locator('#filter-select').getByText('Tâches ouvertes', {
        exact: true,
      }),
    ).toBeVisible();
    // changeLanguage() closed the menu. Reopen it by its French label; the
    // header locators are hardcoded to English.
    await page.getByRole('button', {name: 'Paramètres'}).click();
    await expect(
      page.getByRole('menuitem', {name: 'Déconnexion'}),
    ).toBeVisible();
  });
});
