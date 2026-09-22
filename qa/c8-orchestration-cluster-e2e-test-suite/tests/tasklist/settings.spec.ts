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
    // Assert on always-visible localized chrome (welcome heading, header
    // Settings button, Processes nav link) to confirm the UI switched to
    // French. The former "open tasks" heading and the logout control are no
    // longer a standalone heading / button, so they are not reliable anchors.
    await expect(
      page.getByRole('heading', {name: 'Bienvenue dans Tasklist'}),
    ).toBeVisible();
    await expect(page.getByRole('button', {name: 'Paramètres'})).toBeVisible();
    await expect(page.getByRole('link', {name: 'Processus'})).toBeVisible();
  });
});
