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
import {captureScreenshot, captureFailureVideo} from '@setup';
import {LOGIN_CREDENTIALS} from 'utils/constants';
import {mockOIDCModeUI} from 'utils/mockOIDCModeUI';

test.describe('mapping rules visibility on SaaS', () => {
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('hides mapping rules when no additional IdP is configured', async ({
    page,
    loginPage,
    identityMappingRulesPage,
  }) => {
    await navigateToApp(page, 'admin');
    await mockOIDCModeUI(page, {
      isOidc: 'true',
      organizationId: 'test-org',
      isAdditionalIdpConfigured: 'false',
    });
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );

    await expect(identityMappingRulesPage.mappingRulesNavItem).toBeHidden();
  });

  test('shows mapping rules when an additional IdP is configured', async ({
    page,
    loginPage,
    identityMappingRulesPage,
  }) => {
    await navigateToApp(page, 'admin');
    await mockOIDCModeUI(page, {
      isOidc: 'true',
      organizationId: 'test-org',
      isAdditionalIdpConfigured: 'true',
    });
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );

    await expect(identityMappingRulesPage.mappingRulesNavItem).toBeVisible();
    await identityMappingRulesPage.mappingRulesNavItem.click();
    await expect(identityMappingRulesPage.mappingRulesList).toBeVisible();
  });
});
