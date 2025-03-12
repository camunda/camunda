/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@fixtures/8.7';
import {navigateToApp} from '@pages/8.7/UtilitiesPage';
import {expect} from '@playwright/test';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.describe('Login Tests', () => {
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Basic Login on Operate', async ({
    page,
    operateLoginPage,
    operateHomePage,
  }) => {
    await navigateToApp(page, 'operate');
    await operateLoginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test('Basic Login on TaskList', async ({
    page,
    taskListLoginPage,
    taskPanelPage,
  }) => {
    await navigateToApp(page, 'tasklist');
    await taskListLoginPage.login('demo', 'demo');
    await expect(taskPanelPage.taskListPageBanner).toBeVisible();
  });
});
