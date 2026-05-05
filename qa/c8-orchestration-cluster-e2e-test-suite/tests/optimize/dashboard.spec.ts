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
import {deploy} from 'utils/zeebeClient';
import {sleep} from 'utils/sleep';

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

test.beforeAll(async () => {
  await deploy(['./resources/orderProcess_v_1.bpmn']);
  await sleep(30000);
});

test.describe('Dashboard', () => {
  test.beforeEach(async ({page, optimizeLoginPage, optimizeHomePage}) => {
    await navigateToApp(page, 'optimize');
    await optimizeLoginPage.login('demo', 'demo');
    await expect(optimizeHomePage.createNewButton).toBeVisible({
      timeout: 60000,
    });
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('shouldRenameDashboard', async ({
    page,
    optimizeHomePage,
    optimizeDashboardPage,
  }) => {
    await createBlankDashboard(page, optimizeHomePage, optimizeDashboardPage);
    await expect(optimizeDashboardPage.dashboardName).toBeVisible();

    await optimizeDashboardPage.editButton.click();
    await page.locator('.EntityNameForm .name-input input').fill('New Name');
    await optimizeDashboardPage.save();

    await expect(optimizeDashboardPage.dashboardName).toHaveText('New Name');
  });

  test('shouldCancelDashboardEditing', async ({
    page,
    optimizeHomePage,
    optimizeDashboardPage,
  }) => {
    await createBlankDashboard(page, optimizeHomePage, optimizeDashboardPage);
    const originalName =
      await optimizeDashboardPage.dashboardName.textContent();

    await optimizeDashboardPage.editButton.click();
    await page
      .locator('.EntityNameForm .name-input input')
      .fill('Should Not Be Saved');
    await page.getByRole('button', {name: 'Cancel'}).click();

    await expect(optimizeDashboardPage.dashboardName).toHaveText(originalName!);
  });

  test('shouldCreateReportAndAddToDashboard', async ({
    page,
    optimizeHomePage,
    optimizeDashboardPage,
    optimizeProcessReportPage,
  }) => {
    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Report').click();
    await page.getByRole('button', {name: 'Blank report'}).click();
    await optimizeHomePage.modalConfirmButton.click();
    await optimizeProcessReportPage.save();

    await page.goto(process.env.OPTIMIZE_URL!);
    await expect(optimizeHomePage.createNewButton).toBeVisible();

    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Dashboard').click();
    await page.getByRole('button', {name: 'Blank dashboard'}).click();

    await page.locator('.AddButton').click();
    await page
      .locator('.cds--list-box__menu-item')
      .filter({hasText: 'Blank report'})
      .click();
    await page
      .locator('.CreateTileModal .cds--modal-footer .cds--btn--primary')
      .click();

    await optimizeDashboardPage.save();

    await expect(optimizeDashboardPage.reportTile).toBeVisible();
  });

  test('shouldCreateDashboardFromTemplate', async ({
    page,
    optimizeHomePage,
    optimizeDashboardPage,
  }) => {
    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Dashboard').click();
    await page
      .locator('.Modal .DefinitionSelection input')
      .fill('Order process');
    await expect(
      page
        .locator('.cds--list-box__menu-item')
        .filter({hasText: 'Order process'}),
    ).toBeVisible({timeout: 60000});
    await page
      .locator('.cds--list-box__menu-item')
      .filter({hasText: 'Order process'})
      .click();
    await page
      .locator('.Modal .templateContainer button')
      .filter({hasText: 'Improve productivity'})
      .click();
    await optimizeHomePage.modalConfirmButton.click();
    await optimizeDashboardPage.save();

    await expect(optimizeDashboardPage.dashboardName).toHaveText(
      'Improve productivity',
    );
    await expect(optimizeDashboardPage.reportTile).toBeVisible();
  });

  test('shouldShareDashboard', async ({
    page,
    optimizeHomePage,
    optimizeDashboardPage,
  }) => {
    await createBlankDashboard(page, optimizeHomePage, optimizeDashboardPage);
    await page.locator('.share-button button').click();
    await page.locator('.ShareEntity .cds--toggle__switch').click();

    await expect(page.locator('.ShareEntity input[type="text"]')).toBeVisible();
  });
});
