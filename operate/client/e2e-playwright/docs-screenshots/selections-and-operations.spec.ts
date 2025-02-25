/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../test-fixtures';

import {
  mockBatchOperations,
  mockGroupedProcesses,
  mockStatistics,
  mockResponses as mockProcessesResponses,
  mockOrderProcessInstances,
} from '../mocks/processes.mocks';
import {open} from 'modules/mocks/diagrams';
import {URL_API_PATTERN} from '../constants';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('selections and operations', () => {
  test('select operations to retry', async ({
    page,
    commonPage,
    processesPage,
    processesPage: {filtersPanel},
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        groupedProcesses: mockGroupedProcesses,
        batchOperations: mockBatchOperations,
        processInstances: mockOrderProcessInstances,
        statistics: mockStatistics,
        processXml: open('orderProcess.bpmn'),
      }),
    );

    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        incidents: 'true',
      },
      options: {waitUntil: 'networkidle'},
    });

    await filtersPanel.selectProcess('Order process');
    await filtersPanel.selectVersion('1');
    await filtersPanel.processVersionFilter.blur();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/selections-and-operations/operate-many-instances-with-incident.png',
    });

    const checkboxes = page.getByRole('row', {name: /select row/i});
    await checkboxes.nth(1).locator('label').click();
    await checkboxes.nth(2).locator('label').click();
    await checkboxes.nth(3).locator('label').click();

    const retryButton = await page.getByRole('button', {
      name: 'Retry',
      exact: true,
    });

    await commonPage.addDownArrow(retryButton);
    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/selections-and-operations/operate-select-operation.png',
    });
  });

  test('view operations panel after retry operation', async ({
    page,
    commonPage,
    processesPage,
    processesPage: {filtersPanel},
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        groupedProcesses: mockGroupedProcesses,
        batchOperations: mockBatchOperations,
        processInstances: mockOrderProcessInstances,
        statistics: mockStatistics,
        processXml: open('orderProcess.bpmn'),
      }),
    );

    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        incidents: 'true',
      },
      options: {waitUntil: 'networkidle'},
    });

    await filtersPanel.selectProcess('Order process');
    await filtersPanel.selectVersion('1');
    await filtersPanel.processVersionFilter.blur();

    await commonPage.expandOperationsPanel();

    await processesPage.diagram.moveCanvasHorizontally(-200);
    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/selections-and-operations/operate-operations-panel.png',
    });
  });
});
