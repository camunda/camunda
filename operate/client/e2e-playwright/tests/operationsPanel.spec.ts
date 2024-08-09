/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SETUP_WAITING_TIME} from './constants';
import {createDemoInstances} from './operationsPanel.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {config} from '../config';
import {ENDPOINTS} from './api/endpoints';

let initialData: Awaited<ReturnType<typeof createDemoInstances>>;

test.beforeAll(async ({request}) => {
  test.setTimeout(SETUP_WAITING_TIME);
  initialData = await createDemoInstances();

  const {processInstanceKey} = initialData.singleOperationInstance;

  // wait until single operation instances is created and in incident state
  await expect
    .poll(
      async () => {
        const response = await request.get(
          `${config.endpoint}/v1/process-instances/${processInstanceKey}`,
        );
        const {incident} = await response.json();
        return incident;
      },
      {timeout: SETUP_WAITING_TIME},
    )
    .toBeTruthy();

  // create demo operations
  await Promise.all(
    [...new Array(50)].map(async () => {
      const response = await request.post(
        ENDPOINTS.createOperation(processInstanceKey),
        {
          data: {
            operationType: 'RESOLVE_INCIDENT',
          },
        },
      );

      return response;
    }),
  );

  // wait until all operations are created
  await expect
    .poll(
      async () => {
        const response = await request.post(
          `${config.endpoint}/api/batch-operations`,
          {
            data: {
              pageSize: 50,
            },
          },
        );
        const operations = await response.json();
        return operations.length;
      },
      {timeout: SETUP_WAITING_TIME},
    )
    .toBe(50);
});

test.beforeEach(async ({page, dashboardPage}) => {
  await dashboardPage.navigateToDashboard();
  await page.getByRole('link', {name: /processes/i}).click();
});

test.describe('Operations Panel', () => {
  test('infinite scrolling', async ({page, commonPage}) => {
    await commonPage.expandOperationsPanel();
    await expect(page.getByTestId('operations-entry')).toHaveCount(20);
    await page.getByTestId('operations-entry').nth(19).scrollIntoViewIfNeeded();
    await expect(page.getByTestId('operations-entry')).toHaveCount(40);
  });
});
