/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test} from '../test-fixtures';

import {
  mockBatchOperations,
  mockGroupedProcesses,
  mockProcessInstances,
  mockStatistics,
  mockResponses as mockProcessesResponses,
  mockProcessXml,
  mockProcessInstancesAfterResolvingIncident,
} from '../mocks/processes.mocks';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('selections and operations', () => {
  test('select operations to retry', async ({
    page,
    commonPage,
    processesPage,
  }) => {
    await page.route(
      /^.*\/api.*$/i,
      mockProcessesResponses({
        groupedProcesses: mockGroupedProcesses,
        batchOperations: mockBatchOperations,
        processInstances: mockProcessInstances,
        statistics: mockStatistics,
        processXml: mockProcessXml,
      }),
    );

    await processesPage.navigateToProcesses({
      searchParams: {active: 'true', incidents: 'true'},
      options: {waitUntil: 'networkidle'},
    });

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
    processesPage,
  }) => {
    await page.route(
      /^.*\/api.*$/i,
      mockProcessesResponses({
        groupedProcesses: mockGroupedProcesses,
        batchOperations: mockBatchOperations,
        processInstances: mockProcessInstancesAfterResolvingIncident,
        statistics: mockStatistics,
        processXml: mockProcessXml,
      }),
    );

    await processesPage.navigateToProcesses({
      searchParams: {active: 'true', incidents: 'true'},
      options: {waitUntil: 'networkidle'},
    });

    await page.getByRole('button', {name: /expand operations/i}).click();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/selections-and-operations/operate-operations-panel.png',
    });
  });
});
