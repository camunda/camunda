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

test.describe('Process Report', () => {
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

  test('shouldCreateBlankReport', async ({
    page,
    optimizeHomePage,
    optimizeProcessReportPage,
  }) => {
    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Report').click();
    await page.getByRole('button', {name: 'Blank report'}).click();
    await optimizeHomePage.modalConfirmButton.click();

    await expect(optimizeProcessReportPage.controlPanel).toBeVisible();
  });

  test('shouldCreateAndNameReport', async ({
    page,
    optimizeHomePage,
    optimizeProcessReportPage,
  }) => {
    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Report').click();
    await page.getByRole('button', {name: 'Blank report'}).click();
    await optimizeHomePage.modalConfirmButton.click();

    await page
      .locator('.EntityNameForm .name-input input')
      .fill('Invoice Pipeline');
    await optimizeProcessReportPage.save();

    await expect(optimizeProcessReportPage.reportName).toHaveText(
      'Invoice Pipeline',
    );
  });

  test('shouldShowNoDataNoticeForBlankReport', async ({
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
    await optimizeHomePage.listItemLink('report').click();

    await expect(optimizeHomePage.noDataNotice).toContainText(
      'Report configuration is incomplete',
    );
  });

  test('shouldCreateReportFromTemplate', async ({
    page,
    optimizeHomePage,
    optimizeProcessReportPage,
  }) => {
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

    await expect(optimizeProcessReportPage.controlPanel).toBeVisible();
  });

  test('shouldShareReport', async ({
    page,
    optimizeHomePage,
    optimizeProcessReportPage,
  }) => {
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
    await optimizeProcessReportPage.save();
    await page.locator('.share-button button').click();
    await page.locator('.ShareEntity .cds--toggle__switch').click();

    await expect(page.locator('.ShareEntity input[type="text"]')).toBeVisible();
  });
});
