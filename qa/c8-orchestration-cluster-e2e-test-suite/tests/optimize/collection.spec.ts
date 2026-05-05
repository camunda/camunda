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

async function createCollection(
  page: Page,
  optimizeHomePage: OptimizeHomePage,
  name: string = 'Test Collection',
): Promise<void> {
  await optimizeHomePage.clickCreateNew('Collection');
  await optimizeHomePage.modalNameInput.fill(name);
  await optimizeHomePage.modalConfirmButton.click();
  // Data sources selection step: confirm without selecting any sources
  await optimizeHomePage.modalConfirmButton.click();
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

test.describe('Collection', () => {
  test('shouldCreateCollection', async ({
    page,
    optimizeHomePage,
    optimizeCollectionPage,
  }) => {
    // given / when
    await createCollection(page, optimizeHomePage, 'Test Collection');

    // then
    await expect(optimizeCollectionPage.collectionTitle).toContainText(
      'Test Collection',
    );
  });

  test('shouldRenameCollection', async ({
    page,
    optimizeHomePage,
    optimizeCollectionPage,
  }) => {
    // given
    await createCollection(page, optimizeHomePage);
    await expect(optimizeCollectionPage.collectionTitle).toBeVisible();

    // when
    await optimizeCollectionPage.editName('Renamed Collection');

    // then
    await expect(optimizeCollectionPage.collectionTitle).toContainText(
      'Renamed Collection',
    );
  });

  test('shouldCopyCollection', async ({
    page,
    optimizeHomePage,
    optimizeCollectionPage,
  }) => {
    // given
    await createCollection(page, optimizeHomePage);
    await expect(optimizeCollectionPage.collectionTitle).toBeVisible();

    // when
    await optimizeCollectionPage.copyCollection('Copied Collection');

    // then
    await expect(optimizeCollectionPage.collectionTitle).toContainText(
      'Copied Collection',
    );
  });

  test('shouldDeleteCollection', async ({
    page,
    optimizeHomePage,
    optimizeCollectionPage,
  }) => {
    // given
    await createCollection(page, optimizeHomePage, 'Collection To Delete');
    await expect(optimizeCollectionPage.collectionTitle).toContainText(
      'Collection To Delete',
    );

    // when
    await optimizeCollectionPage.deleteCollection();

    // then - back on home page with the collection removed
    await expect(optimizeHomePage.entityList).toBeVisible();
    await expect(
      optimizeHomePage.listItem('collection').filter({hasText: 'Collection To Delete'}),
    ).not.toBeVisible();
  });

  test.fixme(
    'shouldAddAndDeleteDataSources',
    async ({page, optimizeHomePage, optimizeCollectionPage}) => {
      // Requires process definitions deployed to Zeebe and imported by Optimize.
      // TODO: Deploy 'Big variable process' and 'Order process' BPMNs via
      // deploy() utility, wait for Optimize data import, then enable.
    },
  );

  test.fixme(
    'shouldCreateKpiReport',
    async ({page, optimizeHomePage, optimizeCollectionPage}) => {
      // Requires 'Order process' deployed and imported by Optimize.
      // TODO: Enable after process data seeding is implemented.
    },
  );

  test.fixme(
    'shouldManageUserPermissions',
    async ({page, optimizeHomePage, optimizeCollectionPage}) => {
      // Requires 'Order process' deployed and imported by Optimize.
      // Also requires additional test users configured in Keycloak.
      // TODO: Enable after process data seeding is implemented.
    },
  );
});
