/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {navigateToApp} from '@pages/UtilitiesPage';
import {expect} from '@playwright/test';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.describe.parallel('authorizations page', () => {
  test.beforeEach(async ({page, loginPage}) => {
    await navigateToApp(page, 'identity');
    await loginPage.login('demo', 'demo');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('CRUD authorization for user', async ({
    page,
    identityAuthorizationsPage,
  }) => {
    const userName = 'Test';
    await test.step('Add authorization', async () => {
      //navigate to authorization menu > authorizations
      await identityAuthorizationsPage.clickAuthorizationsTab();
      await identityAuthorizationsPage.clickAuthorizationButton();
      await identityAuthorizationsPage.assertAuthorizationModalPresent();
      await identityAuthorizationsPage.clickAuthorizationOwnerComboBox();
      await identityAuthorizationsPage.selectOwnerComboBox(userName);
      await identityAuthorizationsPage.clickAuthorizationResourceIdField();
      await identityAuthorizationsPage.fillAuthorizationResourceIdField('*');
      await identityAuthorizationsPage.clickAuthorizationAccessPermissionCheckbox(
        'Access',
      );
      await identityAuthorizationsPage.clickAuthorizationButtonInDialog();

      //due to bug, fresh needed
      await page.reload();
      await expect(
        page.getByText(`USER${userName}*ACCESS`).first(),
      ).toBeVisible({
        timeout: 60000,
      });
    })

    await test.step('Remove authorization', async () => {
    })
  });

})
