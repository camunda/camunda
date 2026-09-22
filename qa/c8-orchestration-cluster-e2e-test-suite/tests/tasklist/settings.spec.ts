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
import {extendedAssertionOptions} from 'utils/constants';

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
    // Switching the language re-renders every translated string across the
    // whole app tree, which can outlast the default 10s timeout on a loaded
    // CI runner (confirmed by a failure screenshot showing the page fully in
    // French moments after this assertion had already timed out) -- use the
    // suite's extended budget for that case, same as other load-dependent
    // assertions.
    await expect(
      page.getByRole('heading', {name: 'Bienvenue dans Tasklist'}),
    ).toBeVisible({timeout: extendedAssertionOptions.timeout});
    await expect(
      page.getByRole('heading', {name: 'Tâches ouvertes'}),
    ).toBeVisible();
    await expect(
      page.getByRole('menuitem', {name: 'Déconnexion'}),
    ).toBeVisible();
  });
});
