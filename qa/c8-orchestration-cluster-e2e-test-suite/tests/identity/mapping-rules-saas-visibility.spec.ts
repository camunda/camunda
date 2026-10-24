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
import {LOGIN_CREDENTIALS} from 'utils/constants';
import {mockOIDCModeUI} from 'utils/mockOIDCModeUI';
import {Paths} from 'utils/relativizePath';
import {createGroup} from 'utils/requestHelpers/group-requestHelpers';
import {cleanupGroups} from 'utils/groupsCleanup';

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

  for (const {name, overrides, visible} of [
    {
      name: 'outside SaaS',
      // Explicitly no organization, rather than whatever the server's config holds.
      overrides: {isOidc: 'true', organizationId: ''},
      visible: true,
    },
    {
      name: 'on SaaS',
      overrides: {isOidc: 'true', organizationId: 'test-org'},
      visible: false,
    },
  ]) {
    test(`${visible ? 'shows' : 'hides'} the mapping rules tab of a role and a group ${name}`, async ({
      page,
      loginPage,
    }) => {
      await navigateToApp(page, 'identity');
      await mockOIDCModeUI(page, overrides);
      await loginPage.login(
        LOGIN_CREDENTIALS.username,
        LOGIN_CREDENTIALS.password,
      );
      // login() returns once the form is submitted: wait for the redirect away from the login
      // page, so the navigation below does not race the pending login requests.
      await page.waitForURL((url) => !url.pathname.endsWith('/login'));

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
