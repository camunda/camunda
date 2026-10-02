/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {navigateToApp} from '@pages/UtilitiesPage';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {LOGIN_CREDENTIALS, defaultAssertionOptions} from 'utils/constants';
import {mockOIDCModeUI} from 'utils/mockOIDCModeUI';
import {relativizePath, Paths} from 'utils/relativizePath';

// Regression coverage for https://github.com/camunda/camunda/issues/39155:
// a 401 from an expired session must never show Identity's own (basic-auth)
// login form to an OIDC user — it has no way to authenticate them itself,
// unlike Tasklist/Operate, which stay put and recover through the IdP.
test.describe('OIDC session recovery', () => {
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('does not show the login page when the session expires in OIDC mode', async ({
    page,
    loginPage,
    identityMappingRulesPage,
  }) => {
    await navigateToApp(page, 'identity');
    await mockOIDCModeUI(page);
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );
    await expect(page).toHaveURL(relativizePath(Paths.mappingRules()));

    await page.waitForLoadState('load');

    // Simulate the IdP-side session disappearing (the issue's repro step).
    await page.context().clearCookies();

    // Perform an in-app action that fires a fresh API request, exactly like
    // the issue's repro ("perform an action in the frontend that triggers an
    // API call") — this 401s now that the session cookie is gone.
    const recoveryReload = page.waitForEvent('load');
    const rolesSearchResponse = page.waitForResponse(
      (response) =>
        response.url().includes('/v2/roles/search') &&
        response.request().method() === 'POST',
    );
    await identityMappingRulesPage.rolesNavItem.click();
    const response = await rolesSearchResponse;
    expect(response.status()).toBe(401);
    await recoveryReload;

    await expect(page).toHaveURL(
      relativizePath(Paths.roles()),
      defaultAssertionOptions,
    );
    await expect(loginPage.usernameInput).toBeHidden();
  });
});
