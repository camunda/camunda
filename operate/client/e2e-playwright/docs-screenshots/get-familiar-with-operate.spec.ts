/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  mockOrderProcessInstances,
  mockStatistics,
  mockResponses as mockProcessesResponses,
} from '../mocks/processes.mocks';

import {
  mockResponses as mockProcessInstanceDetailResponses,
  runningOrderProcessInstance,
} from '../mocks/processInstance';
import {open} from 'modules/mocks/diagrams';
import {URL_API_PATTERN} from '../constants';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('get familiar with operate', () => {
  test('view dashboard', async ({page, dashboardPage}) => {
    await page.route(
      URL_API_PATTERN,
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

  test('view processes', async ({
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
      path: 'e2e-playwright/docs-screenshots/get-familiar-with-operate/operate-view-process.png',
    });

    const firstRow = page.getByRole('row', {
      name: new RegExp(
        `view instance ${mockOrderProcessInstances.processInstances[0]?.id}`,
        'i',
      ),
    });

    const cancelOperationButton = firstRow.getByTestId('cancel-operation');

    await commonPage.addRightArrow(cancelOperationButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/get-familiar-with-operate/operate-view-process-cancel.png',
    });

    await commonPage.deleteArrows();

    const processInstanceKeyCell = firstRow.getByTestId(
      'cell-processInstanceKey',
    );

    await commonPage.addRightArrow(processInstanceKeyCell);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/get-familiar-with-operate/operate-process-instance-id.png',
    });
  });

  test('view process instance detail', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessInstanceDetailResponses({
        processInstanceDetail: runningOrderProcessInstance.detail,
        flowNodeInstances: runningOrderProcessInstance.flowNodeInstances,
        statistics: runningOrderProcessInstance.statistics,
        sequenceFlows: runningOrderProcessInstance.sequenceFlows,
        variables: runningOrderProcessInstance.variables,
        xml: runningOrderProcessInstance.xml,
      }),
    );

    await processInstancePage.navigateToProcessInstance({
      id: '225179981395430',
    });

    await page.waitForTimeout(2000);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/get-familiar-with-operate/operate-view-instance-detail.png',
    });
  });
});
