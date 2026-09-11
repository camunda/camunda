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
    // "Tâches ouvertes" ("All open tasks") is not a page heading post-redesign
    // -- FilterSelect.tsx renders every built-in filter, including the
    // default one, as plain visible text inside the filter-select trigger
    // button (id="filter-select"). The button's accessible name is the
    // static "Filtres" (taskFiltersHeaderAria) aria-label, not the filter
    // label, so this has to be a text lookup scoped to that button rather
    // than a heading/accessible-name lookup.
    await expect(
      page.locator('#filter-select').getByText('Tâches ouvertes', {
        exact: true,
      }),
    ).toBeVisible();
    await expect(page.getByRole('button', {name: 'Déconnexion'})).toBeVisible();
  });
});
