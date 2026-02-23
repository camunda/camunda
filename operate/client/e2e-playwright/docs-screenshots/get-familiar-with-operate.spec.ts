/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../visual-fixtures';
import {
  mockIncidentsByError,
  mockProcessDefinitionStatistics,
  mockResponses as mockDashboardResponses,
} from '../mocks/dashboard.mocks';

import {
  mockBatchOperations,
  mockOrderProcessInstances,
  mockProcessDefinitions,
  mockResponses as mockProcessesResponses,
  mockStatistics,
} from '../mocks/processes.mocks';

import {
  mockResponses as mockProcessInstanceDetailResponses,
  runningOrderProcessInstance,
} from '../mocks/processInstance';
import {openFile} from '@/utils/openFile';
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
        incidentsByError: mockIncidentsByError,
        processDefinitionStatistics: mockProcessDefinitionStatistics,
      }),
    );

    await dashboardPage.gotoDashboardPage();

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
        processDefinitions: mockProcessDefinitions,
        batchOperations: mockBatchOperations,
        processInstances: mockOrderProcessInstances,
        statistics: mockStatistics,
        processXml: openFile(
          './e2e-playwright/mocks/resources/orderProcess.bpmn',
        ),
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'true',
      },
    });

    await filtersPanel.selectProcess('Order process');
    await filtersPanel.selectVersion('1');
    await filtersPanel.processVersionFilter.blur();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/get-familiar-with-operate/operate-view-process.png',
    });

    const firstRow = page.getByRole('row', {
      name: new RegExp(
        `view instance ${mockOrderProcessInstances.items[0]?.processInstanceKey}`,
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
        processInstanceDetailV2: runningOrderProcessInstance.detailV2,
        callHierarchy: runningOrderProcessInstance.callHierarchy,
        elementInstances: runningOrderProcessInstance.elementInstances,
        statistics: runningOrderProcessInstance.statistics,
        sequenceFlows: runningOrderProcessInstance.sequenceFlows,
        sequenceFlowsV2: runningOrderProcessInstance.sequenceFlowsV2,
        variables: runningOrderProcessInstance.variables,
        xml: runningOrderProcessInstance.xml,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      id: '225179981395430',
    });

    await page.waitForTimeout(2000);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/get-familiar-with-operate/operate-view-instance-detail.png',
    });
  });
});
