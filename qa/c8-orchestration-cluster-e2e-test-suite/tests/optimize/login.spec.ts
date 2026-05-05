/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {navigateToApp} from '@pages/UtilitiesPage';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.describe('Optimize Login', () => {
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('shouldRedirectToKeycloakLoginPage', async ({page}) => {
    // when
    await navigateToApp(page, 'optimize');

    // then - Keycloak login form is visible
    await expect(page.locator('input[name="username"]')).toBeVisible({
      timeout: 30000,
    });
    await expect(page.locator('input[name="password"]')).toBeVisible();
    await expect(page.locator('[type="submit"]')).toBeVisible();
  });

  test('shouldLoginToOptimizeSuccessfully', async ({
    page,
    optimizeLoginPage,
    optimizeHomePage,
  }) => {
    // given
    await navigateToApp(page, 'optimize');

    // when
    await optimizeLoginPage.login('demo', 'demo');

    // then - Optimize home page is shown with Create New button
    await expect(optimizeHomePage.createNewButton).toBeVisible({
      timeout: 60000,
    });
  });
});
