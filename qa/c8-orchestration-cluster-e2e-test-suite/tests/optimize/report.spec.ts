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

test.describe('Process Report', () => {
  test('shouldCreateBlankReport', async ({
    page,
    optimizeHomePage,
    optimizeProcessReportPage,
  }) => {
    // when - create a blank report
    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Report').click();
    await page.getByRole('button', {name: 'Blank report'}).click();
    await optimizeHomePage.modalConfirmButton.click();

    // then - control panel is visible (edit mode)
    await expect(optimizeProcessReportPage.controlPanel).toBeVisible();
  });

  test('shouldCreateAndNameReport', async ({
    page,
    optimizeHomePage,
    optimizeProcessReportPage,
  }) => {
    // given
    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Report').click();
    await page.getByRole('button', {name: 'Blank report'}).click();
    await optimizeHomePage.modalConfirmButton.click();

    // when - name and save the report
    await page.locator('.EntityNameForm .name-input input').fill('Invoice Pipeline');
    await optimizeProcessReportPage.save();

    // then
    await expect(optimizeProcessReportPage.reportName).toHaveText(
      'Invoice Pipeline',
    );
  });

  test('shouldShowNoDataNoticeForBlankReport', async ({
    page,
    optimizeHomePage,
    optimizeProcessReportPage,
  }) => {
    // given - create and save a blank report
    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Report').click();
    await page.getByRole('button', {name: 'Blank report'}).click();
    await optimizeHomePage.modalConfirmButton.click();
    await optimizeProcessReportPage.save();

    // navigate back to home then into the report view
    await page.goto(process.env.OPTIMIZE_URL!);
    await optimizeHomePage.listItemLink('report').click();

    // then
    await expect(optimizeHomePage.noDataNotice).toContainText(
      'Report configuration is incomplete',
    );
  });

  test.fixme(
    'shouldCreateReportFromTemplate',
    async ({page, optimizeHomePage, optimizeProcessReportPage}) => {
      // Requires 'Order process' deployed and imported by Optimize.
      // TODO: Deploy process BPMN, wait for Optimize import, then enable.
    },
  );

  test.fixme(
    'shouldShareReport',
    async ({page, optimizeHomePage, optimizeProcessReportPage}) => {
      // Requires a report with actual data (process definition deployed +
      // imported by Optimize).
      // TODO: Enable after process data seeding is implemented.
    },
  );
});
