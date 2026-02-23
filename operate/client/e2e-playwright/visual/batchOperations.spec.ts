/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '../visual-fixtures';
import {
  mockBatchOperations,
  mockBatchOperationItems,
  mockBatchOperationItemsWithError,
  mockResponses,
} from '../mocks/batchOperations.mocks';
import {URL_API_PATTERN} from '../constants';
import {clientConfigMock} from '../mocks/clientConfig';

test.beforeEach(async ({context}) => {
  await context.route('**/client-config.js', (route) =>
    route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/javascript;charset=UTF-8',
      },
      body: clientConfigMock,
    }),
  );
});

test.describe('batch operations page', () => {
  test('empty page', async ({page, batchOperationsPage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        batchOperations: {items: [], page: {totalItems: 0}},
      }),
    );

    await batchOperationsPage.gotoBatchOperationsPage();

    await expect(page.getByText('No batch operations found')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('error page', async ({page, batchOperationsPage}) => {
    await page.route(URL_API_PATTERN, mockResponses({}));

    await batchOperationsPage.gotoBatchOperationsPage();

    await expect(page.getByText('Data could not be fetched')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('filled with data', async ({page, batchOperationsPage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        batchOperations: mockBatchOperations,
      }),
    );

    await batchOperationsPage.gotoBatchOperationsPage();

    await expect(batchOperationsPage.batchOperationsTable).toBeVisible();

    await expect(page).toHaveScreenshot();
  });
});

test.describe('batch operation details page', () => {
  test('completed operation', async ({page, batchOperationPage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        batchOperations: {
          items: [mockBatchOperations.items[0]],
          page: {totalItems: 1},
        },
        batchOperationItems: mockBatchOperationItems,
      }),
    );

    await batchOperationPage.gotoBatchOperationPage(
      '653ed5e6-49ed-4675-85bf-2c54a94d8180',
    );

    await expect(batchOperationPage.batchItemsTable).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('operation with errors', async ({page, batchOperationPage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        batchOperations: {
          items: [mockBatchOperations.items[1]],
          page: {totalItems: 1},
        },
        batchOperationItems: mockBatchOperationItemsWithError,
      }),
    );

    await batchOperationPage.gotoBatchOperationPage(
      '35ccdcfc-aeac-4ec8-ac6c-db67e581b22e',
    );

    await expect(batchOperationPage.batchItemsTable).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('active operation', async ({page, batchOperationPage}) => {
    const activeOperation = {
      batchOperationKey: '653ed5e6-49ed-4675-85bf-2c54a94d8199',
      batchOperationType: 'MIGRATE_PROCESS_INSTANCE' as const,
      startDate: '2023-10-01T10:00:00.000+0000',
      endDate: undefined,
      operationsTotalCount: 3,
      operationsFailedCount: 0,
      operationsCompletedCount: 0,
      state: 'ACTIVE' as const,
    };

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        batchOperations: {
          items: [activeOperation],
          page: {totalItems: 1},
        },
        batchOperationItems: {
          items: [
            {
              batchOperationKey: '653ed5e6-49ed-4675-85bf-2c54a94d8199',
              itemKey: 'item-1',
              processInstanceKey: '6755399441062827',
              state: 'ACTIVE',
              operationType: 'MIGRATE_PROCESS_INSTANCE',
            },
          ],
          page: {totalItems: 1},
        },
      }),
    );

    await batchOperationPage.gotoBatchOperationPage(
      '653ed5e6-49ed-4675-85bf-2c54a94d8199',
    );

    await expect(batchOperationPage.batchItemsTable).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('empty operation', async ({page, batchOperationPage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        batchOperations: {
          items: [mockBatchOperations.items[0]],
          page: {totalItems: 1},
        },
        batchOperationItems: {
          items: [],
          page: {totalItems: 0},
        },
      }),
    );

    await batchOperationPage.gotoBatchOperationPage(
      '653ed5e6-49ed-4675-85bf-2c54a94d8180',
    );

    await expect(page.getByText('No items found')).toBeVisible();

    await expect(page).toHaveScreenshot();
  });
});
