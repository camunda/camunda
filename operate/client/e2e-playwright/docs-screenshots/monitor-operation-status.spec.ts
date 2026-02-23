/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../visual-fixtures';
import * as path from 'path';

import {
  mockResponses as mockProcessesResponses,
  mockMigrationOperation,
  mockOrderProcessInstancesWithFailedOperations,
  mockProcessDefinitions,
} from '../mocks/processes.mocks';
import {openFile} from '@/utils/openFile';
import {URL_API_PATTERN} from '../constants';

const baseDirectory =
  'e2e-playwright/docs-screenshots/monitor-operation-status/';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('process instance migration', () => {
  test('migrate process instances', async ({
    page,
    commonPage,
    processesPage,
    processesPage: {processInstancesTable},
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        processDefinitions: mockProcessDefinitions.filter(
          (d) => d.processDefinitionId === 'orderProcess',
        ),
        batchOperations: {
          items: [
            {
              ...mockMigrationOperation,
              endDate: '2023-09-29T16:23:15.684+0000',
              operationsCompletedCount: 1,
            },
          ],
          page: {totalItems: 1},
        },
        processInstances: mockOrderProcessInstancesWithFailedOperations,
        batchOperationItems: {
          items: Array(3)
            .fill(0)
            .map((_, index) => ({
              batchOperationKey: '653ed5e6-49ed-4675-85bf-2c54a94d8180',
              itemKey: `failed-item-${index}`,
              processInstanceKey: `22517998139543${index
                .toString()
                .padStart(2, '0')}`,
              state: 'FAILED' as const,
              processedDate: '2023-09-29T16:23:14.000+0000',
              errorMessage: 'Unable to process operation',
              operationType: 'MIGRATE_PROCESS_INSTANCE' as const,
            })),
          page: {totalItems: 3},
        },
        statistics: {
          items: [
            {
              elementId: 'checkPayment',
              active: 3,
              canceled: 0,
              incidents: 0,
              completed: 0,
            },
          ],
        },
        processXml: openFile(
          './e2e-playwright/mocks/resources/orderProcess_v2.bpmn',
        ),
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'false',
        canceled: 'false',
        completed: 'false',
        operationId: '653ed5e6-49ed-4675-85bf-2c54a94d8180',
      },
    });

    await commonPage.addDownArrow(
      processInstancesTable.getByRole('button', {
        name: /sort by operation state/i,
      }),
    );

    const failedRows = await processInstancesTable
      .getByText(/failed/i)
      .locator('..')
      .all();

    await Promise.all(
      failedRows.map((row) => {
        return commonPage.addRightArrow(row);
      }),
    );

    await page.screenshot({
      path: path.join(baseDirectory, 'operation-state-row.png'),
    });

    await commonPage.deleteArrows();

    await commonPage.addRightArrow(
      processInstancesTable
        .getByRole('button', {name: /expand current row/i})
        .nth(0),
    );

    await page.screenshot({
      path: path.join(baseDirectory, 'expand-row-button.png'),
    });

    await commonPage.deleteArrows();

    await processInstancesTable
      .getByRole('button', {name: /expand current row/i})
      .nth(0)
      .click();

    await commonPage.addRightArrow(
      processInstancesTable.getByText(/unable to process operation/i).nth(0),
    );

    await page.screenshot({
      path: path.join(baseDirectory, 'expanded-instances-row.png'),
    });
  });
});
