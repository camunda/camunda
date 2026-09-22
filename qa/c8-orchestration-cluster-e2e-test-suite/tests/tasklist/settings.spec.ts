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
    // Check the Settings menu's own translated content while it's still
    // open, then close it. Radix's DropdownMenu is modal by default: while
    // open, it marks everything outside itself aria-hidden (confirmed by an
    // error-context.md ARIA snapshot at the moment of a real failure, which
    // contained only the menu's own subtree) -- so the page-content checks
    // below would never find their targets, no matter how long they waited,
    // until the menu closes.
    await expect(
      page.getByRole('menuitem', {name: 'Déconnexion'}),
    ).toBeVisible();
    await page.keyboard.press('Escape');

    await expect(
      page.getByRole('heading', {name: 'Bienvenue dans Tasklist'}),
    ).toBeVisible();
    // "Tâches ouvertes" ("All open tasks") is not a page heading post-redesign
    // -- it's the currently-selected filter's label, rendered as plain text
    // inside the "Filtres" dropdown trigger button, so it has to be a text
    // lookup rather than a heading/accessible-name lookup.
    await expect(
      page.locator('#filter-select').getByText('Tâches ouvertes', {
        exact: true,
      }),
    ).toBeVisible();
  });
});
