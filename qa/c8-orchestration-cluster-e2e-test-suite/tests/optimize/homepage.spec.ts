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

test.describe('Homepage', () => {
  test('shouldShowCreateNewMenuOptions', async ({optimizeHomePage}) => {
    // when
    await optimizeHomePage.createNewButton.click();

    // then
    await expect(optimizeHomePage.menuOption('Collection')).toBeVisible();
    await expect(optimizeHomePage.menuOption('Dashboard')).toBeVisible();
    await expect(optimizeHomePage.menuOption('Report')).toBeVisible();
  });

  test('shouldNavigateToReportViewAndEditPages', async ({
    page,
    optimizeHomePage,
    optimizeProcessReportPage,
  }) => {
    // given - create a blank report and save it
    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Report').click();
    await page.getByRole('button', {name: 'Blank report'}).click();
    await optimizeHomePage.modalConfirmButton.click();
    await optimizeProcessReportPage.save();

    // navigate back to home
    await page.goto(process.env.OPTIMIZE_URL!);
    await expect(optimizeHomePage.entityList).toBeVisible();

    // when - click through to view page
    await optimizeHomePage.listItemLink('report').click();

    // then - empty state is shown for a blank report
    await expect(optimizeHomePage.noDataNotice).toContainText(
      'Report configuration is incomplete',
    );

    // navigate back and open edit via context menu
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
    // given - create a blank dashboard and save it
    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Dashboard').click();
    await page.getByRole('button', {name: 'Blank dashboard'}).click();
    await optimizeDashboardPage.save();

    // navigate back to home
    await page.goto(process.env.OPTIMIZE_URL!);
    await expect(optimizeHomePage.entityList).toBeVisible();

    // when - click through to view page
    await optimizeHomePage.listItemLink('dashboard').click();

    // then - edit button is visible on dashboard view
    await expect(optimizeDashboardPage.editButton).toBeVisible();

    // navigate back and open edit via context menu
    await page.goto(process.env.OPTIMIZE_URL!);
    await optimizeHomePage.listItem('dashboard').hover();
    await optimizeHomePage
      .listItemContextMenu(optimizeHomePage.listItem('dashboard'))
      .click();
    await page.locator('.cds--menu-item').filter({hasText: 'Edit'}).click();

    await expect(optimizeDashboardPage.addTileButton).toBeVisible();
  });

  test.fixme(
    'shouldSearchEntitiesAcrossCollectionsDashboardsAndReports',
    async () => {
      // Requires pre-existing entities with known names.
      // TODO: Seed data (Collection "Sales", Dashboard "Sales Dashboard",
      // Report "Incoming Leads") via Optimize API before enabling.
    },
  );
});
