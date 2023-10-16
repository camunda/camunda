/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test} from '../test-fixtures';
import {
  mockIncidentsByError,
  mockIncidentsByProcess,
  mockStatistics as mockDashboardStatistics,
  mockResponses as mockDashboardResponses,
} from '../mocks/dashboard.mocks';

import {
  mockBatchOperations,
  mockGroupedProcesses,
  mockProcessInstances,
  mockStatistics,
  mockResponses as mockProcessesResponses,
  mockProcessXml,
} from '../mocks/processes.mocks';

import {
  mockResponses as mockProcessInstanceDetailResponses,
  runningInstance,
} from '../mocks/processInstance.mocks';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('get familiar with operate', () => {
  test('view dashboard', async ({page, dashboardPage}) => {
    await page.route(
      /^.*\/api.*$/i,
      mockDashboardResponses({
        statistics: mockDashboardStatistics,
        incidentsByError: mockIncidentsByError,
        incidentsByProcess: mockIncidentsByProcess,
      }),
    );

    await dashboardPage.navigateToDashboard({waitUntil: 'networkidle'});

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/get-familiar-with-operate/operate-introduction.png',
    });
  });

  test('view processes', async ({page, commonPage, processesPage}) => {
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
      path: 'e2e-playwright/docs-screenshots/get-familiar-with-operate/operate-view-process.png',
    });

    const firstRow = await page.getByRole('row', {
      name: new RegExp(
        `view instance ${mockProcessInstances.processInstances[0]?.id}`,
        'i',
      ),
    });

    const cancelOperationButton =
      await firstRow.getByTestId('cancel-operation');

    await commonPage.addRightArrow(cancelOperationButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/get-familiar-with-operate/operate-view-process-cancel.png',
    });

    await commonPage.deleteArrows();

    const processInstanceKeyCell = await firstRow.getByTestId(
      'cell-processInstanceKey',
    );

    await commonPage.addRightArrow(processInstanceKeyCell);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/get-familiar-with-operate/operate-process-instance-id.png',
    });
  });

  test('view process instance detail', async ({page, processInstancePage}) => {
    await page.route(
      /^.*\/api.*$/i,
      mockProcessInstanceDetailResponses({
        processInstanceDetail: runningInstance.detail,
        flowNodeInstances: runningInstance.flowNodeInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        variables: runningInstance.variables,
        xml: runningInstance.xml,
      }),
    );

    await processInstancePage.navigateToProcessInstance({
      id: '2251799813687144',
      options: {waitUntil: 'networkidle'},
    });

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/get-familiar-with-operate/operate-view-instance-detail.png',
    });
  });
});
