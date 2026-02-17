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
  mockBatchOperationItemsWithError,
  mockProcessDefinitions,
  mockProcessInstances,
  mockProcessInstancesWithOperationError,
  mockProcessXml,
  mockResponses,
  mockStatistics,
} from '../mocks/processes.mocks';
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

test.describe('processes page', () => {
  test('empty page', async ({page, processesPage}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        'panelStates',
        JSON.stringify({
          isOperationsCollapsed: false,
        }),
      );
    });

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        batchOperations: {items: [], page: {totalItems: 0}},
        processDefinitions: mockProcessDefinitions,
        batchOperationItems: {items: [], page: {totalItems: 0}},
        statistics: {
          items: [],
        },
        processXml: '',
        processInstances: {
          items: [],
          page: {totalItems: 0},
        },
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'true',
      },
    });

    await expect(page).toHaveScreenshot();
  });

  test('error page', async ({page, processesPage}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        'panelStates',
        JSON.stringify({
          isFiltersCollapsed: true,
          isOperationsCollapsed: false,
        }),
      );
    });

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processDefinitions: mockProcessDefinitions,
        batchOperationItems: {items: [], page: {totalItems: 0}},
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'true',
        process: 'bigVarProcess',
        version: '1',
      },
    });

    await expect(page.getByText('Data could not be fetched')).toHaveCount(2);
    await expect(page).toHaveScreenshot();
  });

  test('filled with data and one flow node selected', async ({
    page,
    processesPage,
    processesPage: {filtersPanel},
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processDefinitions: mockProcessDefinitions,
        batchOperations: mockBatchOperations,
        processInstances: mockProcessInstances,
        batchOperationItems: {items: [], page: {totalItems: 0}},
        statistics: mockStatistics,
        processXml: mockProcessXml,
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'true',
        completed: 'true',
        canceled: 'true',
        process: 'eventSubprocessProcess',
        version: '1',
      },
    });

    await filtersPanel.selectFlowNode('Event Subprocess task');

    await expect(page).toHaveScreenshot();
  });

  test('filled with data and operations panel expanded', async ({
    page,
    processesPage,
  }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        'panelStates',
        JSON.stringify({
          isOperationsCollapsed: false,
        }),
      );
    });

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processDefinitions: mockProcessDefinitions,
        batchOperations: mockBatchOperations,
        processInstances: mockProcessInstances,
        batchOperationItems: {items: [], page: {totalItems: 0}},
        statistics: mockStatistics,
        processXml: mockProcessXml,
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'true',
        completed: 'true',
        canceled: 'true',
        process: 'eventSubprocessProcess',
        version: '1',
      },
    });

    await expect(processesPage.processInstancesTable).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('optional filters visible (part 1)', async ({
    page,
    processesPage,
    processesPage: {filtersPanel},
  }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        'panelStates',
        JSON.stringify({
          isOperationsCollapsed: false,
        }),
      );
    });

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processDefinitions: mockProcessDefinitions,
        batchOperations: mockBatchOperations,
        processInstances: mockProcessInstances,
        batchOperationItems: {items: [], page: {totalItems: 0}},
        statistics: mockStatistics,
        processXml: mockProcessXml,
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'true',
      },
    });

    await filtersPanel.displayOptionalFilter('Variable');
    await filtersPanel.displayOptionalFilter('Error Message');
    await filtersPanel.displayOptionalFilter('Operation Id');
    await filtersPanel.operationIdFilter.type('aaa');
    await expect(
      page.getByText('Id has to be a 16 to 19 digit number or a UUID'),
    ).toBeVisible();
    await expect(page).toHaveScreenshot();
  });

  test('optional filters visible (part 2)', async ({
    page,
    processesPage,
    processesPage: {filtersPanel},
  }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        'panelStates',
        JSON.stringify({
          isOperationsCollapsed: false,
        }),
      );
    });

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processDefinitions: mockProcessDefinitions,
        batchOperations: mockBatchOperations,
        processInstances: mockProcessInstances,
        batchOperationItems: {items: [], page: {totalItems: 0}},
        statistics: mockStatistics,
        processXml: mockProcessXml,
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'true',
      },
    });

    await filtersPanel.displayOptionalFilter('Parent Process Instance Key');
    await filtersPanel.displayOptionalFilter('Process Instance Key(s)');
    await filtersPanel.displayOptionalFilter('Failed job but retries left');
    await filtersPanel.displayOptionalFilter('End Date Range');

    await expect(page).toHaveScreenshot();
  });

  test('data table toolbar visible', async ({page, processesPage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processDefinitions: mockProcessDefinitions,
        batchOperations: mockBatchOperations,
        processInstances: mockProcessInstances,
        batchOperationItems: {items: [], page: {totalItems: 0}},
        statistics: mockStatistics,
        processXml: mockProcessXml,
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'true',
      },
    });

    await page.getByRole('columnheader', {name: 'Select all rows'}).click();

    await expect(page).toHaveScreenshot();
  });

  test('filled with data and active batchOperationId filter', async ({
    page,
    processesPage,
    processesPage: {filtersPanel},
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processDefinitions: mockProcessDefinitions,
        batchOperations: mockBatchOperations,
        processInstances: mockProcessInstancesWithOperationError,
        batchOperationItems: mockBatchOperationItemsWithError,
        statistics: mockStatistics,
        processXml: mockProcessXml,
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'true',
        batchOperationId: 'bf547ac3-9a35-45b9-ab06-b80b43785153',
      },
    });

    await filtersPanel.displayOptionalFilter('Operation Id');
    await filtersPanel.operationIdFilter.type(
      'bf547ac3-9a35-45b9-ab06-b80b43785153',
    );

    await expect(page.getByLabel('Sort by Operation State')).toBeInViewport();

    await expect(page).toHaveScreenshot();
  });

  test('filled with data, active batchOperationId filter and error message expanded', async ({
    page,
    processesPage,
    processesPage: {filtersPanel},
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processDefinitions: mockProcessDefinitions,
        batchOperations: mockBatchOperations,
        processInstances: mockProcessInstancesWithOperationError,
        batchOperationItems: mockBatchOperationItemsWithError,
        statistics: mockStatistics,
        processXml: mockProcessXml,
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'true',
        batchOperationId: 'bf547ac3-9a35-45b9-ab06-b80b43785153',
      },
    });

    await filtersPanel.displayOptionalFilter('Operation Id');
    await filtersPanel.operationIdFilter.type(
      'bf547ac3-9a35-45b9-ab06-b80b43785153',
    );

    const errorRow = page.getByRole('row', {name: '6755399441062827'});

    await expect(errorRow).toBeInViewport();

    await errorRow.getByRole('button', {name: 'Expand current row'}).click();

    await expect(
      page.getByText('Batch Operation Error Message'),
    ).toBeInViewport();

    await expect(page).toHaveScreenshot();
  });
});
