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
import type {Page} from '@playwright/test';
import {deploy} from 'utils/zeebeClient';
import {sleep} from 'utils/sleep';

async function createCollection(
  page: Page,
  optimizeHomePage: OptimizeHomePage,
  name: string = 'Test Collection',
): Promise<void> {
  await optimizeHomePage.clickCreateNew('Collection');
  await optimizeHomePage.modalNameInput.fill(name);
  await optimizeHomePage.modalConfirmButton.click();
  await optimizeHomePage.modalConfirmButton.click();
}

test.beforeAll(async () => {
  await deploy(['./resources/orderProcess_v_1.bpmn']);
  await sleep(30000);
});

test.describe('Collection', () => {
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

  test('shouldCreateCollection', async ({
    page,
    optimizeHomePage,
    optimizeCollectionPage,
  }) => {
    await createCollection(page, optimizeHomePage, 'Test Collection');

    await expect(optimizeCollectionPage.collectionTitle).toContainText(
      'Test Collection',
    );
  });

  test('shouldRenameCollection', async ({
    page,
    optimizeHomePage,
    optimizeCollectionPage,
  }) => {
    await createCollection(page, optimizeHomePage);
    await expect(optimizeCollectionPage.collectionTitle).toBeVisible();

    await optimizeCollectionPage.editName('Renamed Collection');

    await expect(optimizeCollectionPage.collectionTitle).toContainText(
      'Renamed Collection',
    );
  });

  test('shouldCopyCollection', async ({
    page,
    optimizeHomePage,
    optimizeCollectionPage,
  }) => {
    await createCollection(page, optimizeHomePage);
    await expect(optimizeCollectionPage.collectionTitle).toBeVisible();

    await optimizeCollectionPage.copyCollection('Copied Collection');

    await expect(optimizeCollectionPage.collectionTitle).toContainText(
      'Copied Collection',
    );
  });

  test('shouldDeleteCollection', async ({
    page,
    optimizeHomePage,
    optimizeCollectionPage,
  }) => {
    await createCollection(page, optimizeHomePage, 'Collection To Delete');
    await expect(optimizeCollectionPage.collectionTitle).toContainText(
      'Collection To Delete',
    );

    await optimizeCollectionPage.deleteCollection();

    await expect(optimizeHomePage.entityList).toBeVisible();
    await expect(
      optimizeHomePage
        .listItem('collection')
        .filter({hasText: 'Collection To Delete'}),
    ).toBeHidden();
  });

  test('shouldAddAndDeleteDataSources', async ({
    page,
    optimizeHomePage,
    optimizeCollectionPage,
  }) => {
    await createCollection(page, optimizeHomePage);
    await expect(optimizeCollectionPage.collectionTitle).toBeVisible();
    await optimizeCollectionPage.sourcesTab.click();
    await optimizeCollectionPage.emptyStateAddButton.click();
    await optimizeCollectionPage.sourceModalSearchField.fill('Order process');
    await expect(
      page
        .locator('.SourcesModal .cds--list-box__menu-item')
        .filter({hasText: 'Order process'}),
    ).toBeVisible({timeout: 60000});
    await optimizeCollectionPage.selectAllCheckbox.click();
    await optimizeCollectionPage.modalConfirmButton.click();

    const sourceRow = page
      .locator('.EntityList tbody tr')
      .filter({hasText: 'Order process'});
    await expect(sourceRow).toBeVisible();

    await optimizeCollectionPage.selectAllCheckbox.click();
    await optimizeCollectionPage.bulkRemoveButton.click();
    await optimizeCollectionPage.modalConfirmButton.click();

    await expect(sourceRow).toBeHidden();
  });

  test('shouldCreateKpiReport', async ({
    page,
    optimizeHomePage,
    optimizeCollectionPage,
  }) => {
    await createCollection(page, optimizeHomePage);
    await expect(optimizeCollectionPage.collectionTitle).toBeVisible();
    await optimizeHomePage.createNewButton.click();
    await optimizeHomePage.menuOption('Process KPI').click();
    await page.locator('input#KpiSelectionComboBox').fill('Automation rate');
    await page
      .locator('.cds--list-box__menu-item')
      .filter({hasText: 'Automation rate'})
      .click();
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
    await optimizeHomePage.modalConfirmButton.click();
    await optimizeHomePage.modalConfirmButton.click();

    await expect(page.locator('.edit-button')).toBeVisible({timeout: 10000});
  });

  test('shouldManageUserPermissions', async ({
    page,
    optimizeHomePage,
    optimizeCollectionPage,
  }) => {
    await createCollection(page, optimizeHomePage);
    await expect(optimizeCollectionPage.collectionTitle).toBeVisible();
    await optimizeCollectionPage.userTab.click();

    const demoUserRow = page
      .locator('.EntityList tbody tr')
      .filter({hasText: 'demo'});
    await expect(demoUserRow).toBeVisible();
    await expect(demoUserRow).toContainText('Manager');
    await expect(optimizeCollectionPage.addButton).toBeVisible();
  });
});
