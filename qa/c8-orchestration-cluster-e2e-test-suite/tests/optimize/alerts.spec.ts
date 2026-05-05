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

test.beforeEach(async ({page, optimizeLoginPage, optimizeHomePage}) => {
  await navigateToApp(page, 'optimize');
  await optimizeLoginPage.login('demo', 'demo');
  await expect(optimizeHomePage.createNewButton).toBeVisible({timeout: 60000});
});

test.afterEach(async ({page}, testInfo) => {
  await captureScreenshot(page, testInfo);
  await captureFailureVideo(page, testInfo);
});

test.describe('Alerts', () => {
  test.fixme(
    'shouldCreateEditCopyAndRemoveAlert',
    async ({
      page,
      optimizeHomePage,
      optimizeCollectionPage,
      optimizeProcessReportPage,
    }) => {
      // This test requires:
      // 1. 'Order process' deployed to Zeebe and imported by Optimize
      // 2. A number-type report (Process instance count) created in a collection
      //
      // Once data seeding is implemented:
      // - Create a collection with 'Order process' as data source
      // - Create a "Process instance count" report named 'Number Report'
      // - Navigate to the collection's Alerts tab
      // - CREATE: click newAlertButton, fill name, email, select report, confirm
      // - assert alert is listed with name, report, and email
      // - EDIT: hover alert, open context menu, click Edit, change name, confirm
      // - assert updated name is shown
      // - COPY: hover alert, open context menu, click Copy, enter new name, confirm
      // - assert copied alert is listed
      // - DELETE: hover alert, open context menu, click Delete, confirm
      // - assert alert is no longer listed
      //
      // TODO: Enable after process data seeding is implemented.
    },
  );
});
