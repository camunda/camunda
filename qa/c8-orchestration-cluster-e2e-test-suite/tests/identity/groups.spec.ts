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

test.describe('Groups page', () => {
  test.beforeEach(async ({page, loginPage, identityGroupsPage}) => {
    await navigateToApp(page, 'identity');
    await loginPage.login('demo', 'demo');
    await expect(identityGroupsPage.groupsList).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('As an Admin user can create a group with particular permissions and assign it to Test user', async ({
    page,
    identityGroupsPage,
    identityAuthorizationsPage,
  }) => {
    await identityGroupsPage.navigateToGroups();
    await identityGroupsPage.createGroup(
      'testGroup',
      'testGroup',
      'This is a test group',
    );
    await page.reload();
    await identityGroupsPage.assertGroupExists('testGroup');
    await identityGroupsPage.clickGroupId('testGroup');
    await identityGroupsPage.assignUserToGroup('lisa', 'lisa@example.com');
    await page.reload();
    await identityAuthorizationsPage.navigateToAuthorizations();
    await identityAuthorizationsPage.clickResourceType('Authorization');
    await identityAuthorizationsPage.clickCreateAuthorizationButton();
    await identityAuthorizationsPage.clickOwnerTypeDropdown();
    await identityAuthorizationsPage.selectOwnerTypeFromDrowdown('Group');
    await identityAuthorizationsPage.clickOwnerDropdown();
    await identityAuthorizationsPage.selectOwnerFromDropdown('testGroup');
    await identityAuthorizationsPage.fillResourceId('*');
    await identityAuthorizationsPage.checkAccessPermissions([
      'Update',
      'Read',
      'Create',
      'Delete',
    ]);
    await identityAuthorizationsPage.clickCreateAuthorizationSubmitButton();
    await page.reload();
    await identityAuthorizationsPage.clickResourceType('Authorization');
    await identityAuthorizationsPage.assertAuthorizationExists([
      'Group',
      'testGroup',
    ]);
  });
});
