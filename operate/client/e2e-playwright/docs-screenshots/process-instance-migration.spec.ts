/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../test-fixtures';
import * as path from 'path';

import {
  mockGroupedProcesses,
  mockResponses as mockProcessesResponses,
  mockOrderProcessInstances,
  mockOrderProcessV2Instances,
  mockMigrationOperation,
} from '../mocks/processes.mocks';
import {open} from 'modules/mocks/diagrams';
import {expect} from '@playwright/test';
import {URL_API_PATTERN} from '../constants';

const baseDirectory =
  'e2e-playwright/docs-screenshots/process-instance-migration/';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('process instance migration', () => {
  test('migrate process instances', async ({
    page,
    commonPage,
    processesPage,
    processesPage: {filtersPanel},
    migrationView,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        groupedProcesses: mockGroupedProcesses.filter((process) => {
          return process.bpmnProcessId === 'orderProcess';
        }),
        batchOperations: [],
        processInstances: mockOrderProcessInstances,
        statistics: [
          {
            activityId: 'checkPayment',
            active: 20,
            canceled: 0,
            incidents: 0,
            completed: 0,
          },
        ],
        processXml: open('orderProcess_v3.bpmn'),
      }),
    );

    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        incidents: 'false',
        canceled: 'false',
        completed: 'false',
      },
      options: {
        waitUntil: 'networkidle',
      },
    });

    await commonPage.addLeftArrow(filtersPanel.processNameFilter);
    await commonPage.addLeftArrow(filtersPanel.processVersionFilter);

    await page.screenshot({
      path: path.join(baseDirectory, 'process-filters.png'),
    });

    await commonPage.deleteArrows();

    await processesPage.navigateToProcesses({
      searchParams: {
        process: 'orderProcess',
        version: '1',
        active: 'true',
        incidents: 'false',
        canceled: 'false',
        completed: 'false',
      },
      options: {
        waitUntil: 'networkidle',
      },
    });

    await processesPage.getNthProcessInstanceCheckbox(3).click();
    await processesPage.getNthProcessInstanceCheckbox(4).click();
    await processesPage.getNthProcessInstanceCheckbox(5).click();

    await commonPage.addRightArrow(
      processesPage.getNthProcessInstanceCheckbox(3),
    );
    await commonPage.addRightArrow(
      processesPage.getNthProcessInstanceCheckbox(4),
    );
    await commonPage.addRightArrow(
      processesPage.getNthProcessInstanceCheckbox(5),
    );
    await commonPage.addDownArrow(processesPage.migrateButton);

    await page.screenshot({
      path: path.join(baseDirectory, 'migrate-button.png'),
    });

    await commonPage.deleteArrows();

    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        statistics: [
          {
            activityId: 'checkPayment',
            active: 3,
            canceled: 0,
            incidents: 0,
            completed: 0,
          },
        ],
        groupedProcesses: mockGroupedProcesses.filter((process) => {
          return process.bpmnProcessId === 'orderProcess';
        }),
        processXml: open('orderProcess_v2.bpmn'),
      }),
    );

    await processesPage.migrateButton.click();
    await processesPage.migrationModal.confirmButton.click();

    await migrationView.selectTargetProcess('Order process');

    await commonPage.addDownArrow(migrationView.targetProcessComboBox);
    await commonPage.addDownArrow(migrationView.targetVersionDropdown);

    await page.screenshot({
      path: path.join(baseDirectory, 'select-target-process.png'),
    });

    await commonPage.deleteArrows();

    await migrationView.mapFlowNode({
      sourceFlowNodeName: 'Check payment',
      targetFlowNodeName: 'Check payment',
    });

    await migrationView.mapFlowNode({
      sourceFlowNodeName: 'Ship Articles',
      targetFlowNodeName: 'Ship Articles',
    });

    await migrationView.mapFlowNode({
      sourceFlowNodeName: 'Request for payment',
      targetFlowNodeName: 'Request for payment',
    });

    await commonPage.addRightArrow(
      page.getByLabel(`Target flow node for Check payment`),
    );
    await commonPage.addRightArrow(
      page.getByLabel(`Target flow node for Ship Articles`),
    );
    await commonPage.addRightArrow(
      page.getByLabel(`Target flow node for Request for payment`),
    );

    await page.screenshot({
      path: path.join(baseDirectory, 'map-elements.png'),
    });

    await commonPage.deleteArrows();

    await migrationView.selectTargetSourceFlowNode('Check payment');

    const flowNodes = page
      .getByTestId('diagram')
      .getByText('Check payment', {exact: true});

    await commonPage.addDownArrow(flowNodes.first());
    await commonPage.addDownArrow(flowNodes.nth(1));

    await page.screenshot({
      path: path.join(baseDirectory, 'highlight-mapping.png'),
    });

    await commonPage.deleteArrows();

    await migrationView.nextButton.click();

    await commonPage.addUpArrow(page.getByTestId('state-overlay-active'));
    await commonPage.addUpArrow(page.getByTestId('modifications-overlay'));

    await page.screenshot({
      path: path.join(baseDirectory, 'summary.png'),
    });

    await commonPage.deleteArrows();

    await page.route(
      URL_API_PATTERN,
      mockProcessesResponses({
        groupedProcesses: mockGroupedProcesses.filter((process) => {
          return process.bpmnProcessId === 'orderProcess';
        }),
        batchOperations: [
          {
            ...mockMigrationOperation,
            endDate: '2023-09-29T16:23:15.684+0000',
            operationsFinishedCount: 1,
          },
        ],
        batchOperation: mockMigrationOperation,
        processInstances: mockOrderProcessV2Instances,
        statistics: [
          {
            activityId: 'checkPayment',
            active: 3,
            canceled: 0,
            incidents: 0,
            completed: 0,
          },
        ],
        processXml: open('orderProcess_v2.bpmn'),
      }),
    );

    await migrationView.confirmButton.click();

    await migrationView.confirmMigration();

    await processesPage.diagram.moveCanvasHorizontally(-200);

    await expect(
      page.getByTestId('state-overlay-checkPayment-active'),
    ).toBeVisible();
    await expect(page.getByText(mockMigrationOperation.id)).toHaveCount(1);

    await page.screenshot({
      path: path.join(baseDirectory, 'operations-panel.png'),
    });
  });
});
