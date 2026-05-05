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
import {deploy} from 'utils/zeebeClient';
import {sleep} from 'utils/sleep';

test.beforeAll(async () => {
  await deploy(['./resources/orderProcess_v_1.bpmn']);
  await sleep(30000);
});

test.describe('Alerts', () => {
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

  test('shouldCreateEditCopyAndRemoveAlert', async ({
    page,
    optimizeHomePage,
    optimizeCollectionPage,
    optimizeProcessReportPage,
  }) => {
    await optimizeHomePage.clickCreateNew('Collection');
    await optimizeHomePage.modalNameInput.fill('Alert Test Collection');
    await optimizeHomePage.modalConfirmButton.click();
    await optimizeHomePage.modalConfirmButton.click();
    await expect(optimizeCollectionPage.collectionTitle).toBeVisible();

    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Report').click();
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
      .first()
      .click();
    await page.locator('.TemplateModal .cds--radio-button').first().click();
    await optimizeHomePage.modalConfirmButton.click();
    await page
      .locator('.EntityNameForm .name-input input')
      .fill('Count Report');
    await optimizeProcessReportPage.save();

    await page.goto(process.env.OPTIMIZE_URL!);
    await expect(optimizeHomePage.entityList).toBeVisible();
    await optimizeHomePage
      .listItem('collection')
      .filter({hasText: 'Alert Test Collection'})
      .locator('td:nth-child(2) a')
      .click();
    await expect(optimizeCollectionPage.collectionTitle).toBeVisible();
    await optimizeCollectionPage.alertTab.click();
    await optimizeCollectionPage.addButton.click();
    await page
      .locator('.AlertModal')
      .getByLabel('Alert name')
      .fill('Test Alert');
    await page
      .locator('.AlertModal')
      .getByLabel('Send email to')
      .fill('demo@example.com');
    await page
      .locator('.AlertModal .cds--combo-box input')
      .fill('Count Report');
    await page
      .locator('.cds--list-box__menu-item')
      .filter({hasText: 'Count Report'})
      .click();
    await optimizeCollectionPage.modalConfirmButton.click();

    const alertList = page.locator('.AlertList');
    await expect(alertList).toContainText('Test Alert');

    const alertRow = alertList.locator('tbody tr').first();
    await alertRow.hover();
    await alertRow.locator('button.cds--overflow-menu').click();
    await page
      .locator('.cds--overflow-menu-options__option')
      .filter({hasText: 'Edit'})
      .click();
    await page
      .locator('.AlertModal')
      .getByLabel('Alert name')
      .fill('Saved Alert');
    await optimizeCollectionPage.modalConfirmButton.click();

    await expect(alertList).toContainText('Saved Alert');

    await alertRow.hover();
    await alertRow.locator('button.cds--overflow-menu').click();
    await page
      .locator('.cds--overflow-menu-options__option')
      .filter({hasText: 'Copy'})
      .click();
    await optimizeCollectionPage.modalNameInput.fill('Copied Alert');
    await optimizeCollectionPage.modalConfirmButton.click();

    await expect(alertList).toContainText('Copied Alert');

    await alertRow.hover();
    await alertRow.locator('button.cds--overflow-menu').click();
    await page
      .locator('.cds--overflow-menu-options__option')
      .filter({hasText: 'Delete'})
      .click();
    await optimizeCollectionPage.modalConfirmButton.click();

    await expect(alertList).not.toContainText('Saved Alert');
  });
});
