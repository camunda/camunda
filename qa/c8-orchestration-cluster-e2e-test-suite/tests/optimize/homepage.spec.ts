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

test.describe('Homepage', () => {
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

  test('shouldShowCreateNewMenuOptions', async ({optimizeHomePage}) => {
    await optimizeHomePage.createNewButton.click();

    await expect(optimizeHomePage.menuOption('Collection')).toBeVisible();
    await expect(optimizeHomePage.menuOption('Dashboard')).toBeVisible();
    await expect(optimizeHomePage.menuOption('Report')).toBeVisible();
  });

  test('shouldNavigateToReportViewAndEditPages', async ({
    page,
    optimizeHomePage,
    optimizeProcessReportPage,
  }) => {
    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Report').click();
    await page.getByRole('button', {name: 'Blank report'}).click();
    await optimizeHomePage.modalConfirmButton.click();
    await optimizeProcessReportPage.save();

    await page.goto(process.env.OPTIMIZE_URL!);
    await expect(optimizeHomePage.entityList).toBeVisible();

    await optimizeHomePage.listItemLink('report').click();

    await expect(optimizeHomePage.noDataNotice).toContainText(
      'Report configuration is incomplete',
    );

    await page.goto(process.env.OPTIMIZE_URL!);
    await optimizeHomePage.listItem('report').hover();
    await optimizeHomePage
      .listItemContextMenu(optimizeHomePage.listItem('report'))
      .click();
    await page.locator('.cds--menu-item').filter({hasText: 'Edit'}).click();

    await expect(optimizeProcessReportPage.controlPanel).toBeVisible();
  });

  test('shouldNavigateToDashboardViewAndEditPages', async ({
    page,
    optimizeHomePage,
    optimizeDashboardPage,
  }) => {
    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Dashboard').click();
    await page.getByRole('button', {name: 'Blank dashboard'}).click();
    await optimizeDashboardPage.save();

    await page.goto(process.env.OPTIMIZE_URL!);
    await expect(optimizeHomePage.entityList).toBeVisible();

    await optimizeHomePage.listItemLink('dashboard').click();

    await expect(optimizeDashboardPage.editButton).toBeVisible();

    await page.goto(process.env.OPTIMIZE_URL!);
    await optimizeHomePage.listItem('dashboard').hover();
    await optimizeHomePage
      .listItemContextMenu(optimizeHomePage.listItem('dashboard'))
      .click();
    await page.locator('.cds--menu-item').filter({hasText: 'Edit'}).click();

    await expect(page.locator('.AddButton')).toBeVisible();
  });

  test('shouldSearchEntitiesAcrossCollectionsDashboardsAndReports', async ({
    page,
    optimizeHomePage,
    optimizeDashboardPage,
    optimizeProcessReportPage,
  }) => {
    await optimizeHomePage.clickCreateNew('Collection');
    await optimizeHomePage.modalNameInput.fill('Alpha-Search-Collection');
    await optimizeHomePage.modalConfirmButton.click();
    await optimizeHomePage.modalConfirmButton.click();

    await page.goto(process.env.OPTIMIZE_URL!);
    await expect(optimizeHomePage.createNewButton).toBeVisible();

    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Dashboard').click();
    await page.getByRole('button', {name: 'Blank dashboard'}).click();
    await page
      .locator('.EntityNameForm .name-input input')
      .fill('Alpha-Search-Dashboard');
    await optimizeDashboardPage.save();

    await page.goto(process.env.OPTIMIZE_URL!);
    await expect(optimizeHomePage.createNewButton).toBeVisible();

    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Report').click();
    await page.getByRole('button', {name: 'Blank report'}).click();
    await optimizeHomePage.modalConfirmButton.click();
    await page
      .locator('.EntityNameForm .name-input input')
      .fill('Alpha-Search-Report');
    await optimizeProcessReportPage.save();

    await page.goto(process.env.OPTIMIZE_URL!);
    await expect(optimizeHomePage.entityList).toBeVisible();

    await optimizeHomePage.searchField.fill('Alpha-Search');

    await expect(
      page
        .locator('.EntityList tbody tr')
        .filter({hasText: 'Alpha-Search-Collection'}),
    ).toBeVisible();
    await expect(
      page
        .locator('.EntityList tbody tr')
        .filter({hasText: 'Alpha-Search-Dashboard'}),
    ).toBeVisible();
    await expect(
      page
        .locator('.EntityList tbody tr')
        .filter({hasText: 'Alpha-Search-Report'}),
    ).toBeVisible();

    await optimizeHomePage.searchField.fill('Alpha-Search-Dashboard');

    await expect(
      page
        .locator('.EntityList tbody tr')
        .filter({hasText: 'Alpha-Search-Dashboard'}),
    ).toBeVisible();
    await expect(
      page
        .locator('.EntityList tbody tr')
        .filter({hasText: 'Alpha-Search-Collection'}),
    ).toBeHidden();
    await expect(
      page
        .locator('.EntityList tbody tr')
        .filter({hasText: 'Alpha-Search-Report'}),
    ).toBeHidden();

    await optimizeHomePage.searchField.clear();

    await expect(optimizeHomePage.entityList).toBeVisible();
  });
});
