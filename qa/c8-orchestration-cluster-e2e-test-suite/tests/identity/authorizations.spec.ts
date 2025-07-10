/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {navigateToApp} from '@pages/UtilitiesPage';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {waitForItemInList} from '../../utils/waitForItemInList';

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
      await identityAuthorizationsPage.clickAuthorizationsTab();
      await identityAuthorizationsPage.clickAuthorizationButton();
      await identityAuthorizationsPage.assertAuthorizationModalPresent();
      await identityAuthorizationsPage.clickAuthorizationOwnerComboBox();
      await identityAuthorizationsPage.selectOwnerComboBox(userName);
      await identityAuthorizationsPage.clickAuthorizationResourceIdField();
      await identityAuthorizationsPage.fillAuthorizationResourceIdField('*');
      await identityAuthorizationsPage.clickAuthorizationAccessPermissionCheckbox(
        'access',
      );
      await identityAuthorizationsPage.clickAuthorizationButtonInDialog();
      await waitForItemInList(
        page,
        page.getByText(`USER${userName}*ACCESS`).first(),
      );
    });

    await test.step('Remove authorization', async () => {
      // navigate to Authorization menu

      // open application resource type menu

      // await the user authorization for applications here

      // click delet authorization

      // expoect authorization to be removed
      await identityAuthorizationsPage.clickDeleteAuthorizationButton(
    })
  });
});
