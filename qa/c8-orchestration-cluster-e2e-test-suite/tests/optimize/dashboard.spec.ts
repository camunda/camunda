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
import type {OptimizeHomePage} from '@pages/OptimizeHomePage';
import type {OptimizeDashboardPage} from '@pages/OptimizeDashboardPage';
import type {Page} from '@playwright/test';

async function createBlankDashboard(
  page: Page,
  optimizeHomePage: OptimizeHomePage,
  optimizeDashboardPage: OptimizeDashboardPage,
): Promise<void> {
  await optimizeHomePage.createNewButton.click();
  await optimizeHomePage.menuOption('Dashboard').click();
  await page.getByRole('button', {name: 'Blank dashboard'}).click();
  await optimizeDashboardPage.save();
}

test.beforeEach(async ({page, optimizeLoginPage, optimizeHomePage}) => {
  await navigateToApp(page, 'optimize');
  await optimizeLoginPage.login('demo', 'demo');
  await expect(optimizeHomePage.createNewButton).toBeVisible({timeout: 60000});
});

test.afterEach(async ({page}, testInfo) => {
  await captureScreenshot(page, testInfo);
  await captureFailureVideo(page, testInfo);
});

test.describe('Dashboard', () => {
  test('shouldRenameDashboard', async ({
    page,
    optimizeHomePage,
    optimizeDashboardPage,
  }) => {
    // given
    await createBlankDashboard(page, optimizeHomePage, optimizeDashboardPage);
    await expect(optimizeDashboardPage.dashboardName).toBeVisible();

    // when - enter edit mode and rename
    await optimizeDashboardPage.editButton.click();
    await page
      .locator('.EntityNameForm .name-input input')
      .fill('New Name');
    await optimizeDashboardPage.save();

    // then
    await expect(optimizeDashboardPage.dashboardName).toHaveText('New Name');
  });

  test('shouldCancelDashboardEditing', async ({
    page,
    optimizeHomePage,
    optimizeDashboardPage,
  }) => {
    // given
    await createBlankDashboard(page, optimizeHomePage, optimizeDashboardPage);
    const originalName = await optimizeDashboardPage.dashboardName.textContent();

    // when - edit then cancel
    await optimizeDashboardPage.editButton.click();
    await page
      .locator('.EntityNameForm .name-input input')
      .fill('Should Not Be Saved');
    await page.getByRole('button', {name: 'Cancel'}).click();

    // then - name is unchanged
    await expect(optimizeDashboardPage.dashboardName).toHaveText(
      originalName!,
    );
  });

  test('shouldCreateReportAndAddToDashboard', async ({
    page,
    optimizeHomePage,
    optimizeDashboardPage,
    optimizeProcessReportPage,
  }) => {
    // given - create a blank report
    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Report').click();
    await page.getByRole('button', {name: 'Blank report'}).click();
    await optimizeHomePage.modalConfirmButton.click();
    await optimizeProcessReportPage.save();

    // go back to home
    await page.goto(process.env.OPTIMIZE_URL!);
    await expect(optimizeHomePage.createNewButton).toBeVisible();

    // when - create a blank dashboard and add the report
    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Dashboard').click();
    await page.getByRole('button', {name: 'Blank dashboard'}).click();

    // add report tile via the add tile button
    await page.locator('.AddButton').click();
    await page
      .locator('.cds--list-box__menu-item')
      .filter({hasText: 'Blank report'})
      .click();
    await page
      .locator('.CreateTileModal .cds--modal-footer .cds--btn--primary')
      .click();

    await optimizeDashboardPage.save();

    // then - report tile is visible
    await expect(optimizeDashboardPage.reportTile).toBeVisible();
  });

  test.fixme(
    'shouldCreateDashboardFromTemplate',
    async ({page, optimizeHomePage, optimizeDashboardPage}) => {
      // Requires 'Order process' deployed and imported by Optimize.
      // TODO: Deploy process BPMN, wait for Optimize import, then enable.
    },
  );

  test.fixme(
    'shouldShareDashboard',
    async ({page, optimizeHomePage, optimizeDashboardPage}) => {
      // Requires 'Order process' deployed, imported, and at least one
      // dashboard created from a template.
      // TODO: Enable after process data seeding is implemented.
    },
  );
});
