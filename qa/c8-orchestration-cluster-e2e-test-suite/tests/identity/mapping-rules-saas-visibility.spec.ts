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
import {relativizePath, Paths} from 'utils/relativizePath';
import {createGroup} from 'utils/requestHelpers/group-requestHelpers';
import {cleanupGroups} from 'utils/groupsCleanup';

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

    // Anchor on an unconditional nav item: toBeHidden() alone also passes if the sidebar never renders.
    await expect(identityMappingRulesPage.rolesNavItem).toBeVisible();
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

    // Regression guard: unhiding the route must not change the index redirect.
    await expect(page).not.toHaveURL(relativizePath(Paths.mappingRules()));

    await expect(identityMappingRulesPage.mappingRulesNavItem).toBeVisible();
    await identityMappingRulesPage.mappingRulesNavItem.click();

    // URL + Create button hold whether or not rules already exist on this shared cluster.
    await expect(page).toHaveURL(relativizePath(Paths.mappingRules()));
    await expect(
      identityMappingRulesPage.createMappingRuleButton,
    ).toBeVisible();
  });
});

test.describe('mapping rules tab of role and group details on SaaS', () => {
  // The default admin role always exists; a group is created for these tests.
  const ROLE_ID = 'admin';
  const state: Record<string, unknown> = {};

  test.beforeAll(async ({request}) => {
    await createGroup(request, state, 'MappingRulesTab');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test.afterAll(async ({request}) => {
    await cleanupGroups(request, [state.groupIdMappingRulesTab as string]);
  });

  for (const {isAdditionalIdpConfigured, visible} of [
    {isAdditionalIdpConfigured: 'false', visible: false},
    {isAdditionalIdpConfigured: 'true', visible: true},
  ]) {
    test(`${visible ? 'shows' : 'hides'} the mapping rules tab of a role and a group when isAdditionalIdpConfigured=${isAdditionalIdpConfigured}`, async ({
      page,
      loginPage,
    }) => {
      await navigateToApp(page, 'admin');
      await mockOIDCModeUI(page, {
        isOidc: 'true',
        organizationId: 'test-org',
        isAdditionalIdpConfigured,
      });
      await loginPage.login(
        LOGIN_CREDENTIALS.username,
        LOGIN_CREDENTIALS.password,
      );

      for (const detailsPath of [
        `${Paths.roles()}/${ROLE_ID}`,
        `${Paths.groups()}/${state.groupIdMappingRulesTab as string}`,
      ]) {
        await page.goto(process.env.CORE_APPLICATION_URL + detailsPath);

        // Anchor on a tab that is always there: a count of 0 alone also passes if the tabs never render.
        await expect(page.getByRole('tab', {name: 'Clients'})).toBeVisible();
        await expect(
          page.getByRole('tab', {name: 'Mapping rules'}),
        ).toHaveCount(visible ? 1 : 0);
      }
    });
  }
});
