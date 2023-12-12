/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
    migrationMode,
  }) => {
    await page.route(
      /^.*\/api.*$/i,
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
        processXml: open('orderProcess.bpmn'),
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

    await commonPage.addLeftArrow(processesPage.processNameFilter);
    await commonPage.addLeftArrow(processesPage.processVersionFilter);

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

    const checkboxes = page.getByRole('row', {name: /select row/i});
    await checkboxes.nth(3).locator('label').click();
    await checkboxes.nth(4).locator('label').click();
    await checkboxes.nth(5).locator('label').click();

    await commonPage.addRightArrow(checkboxes.nth(3).locator('label'));
    await commonPage.addRightArrow(checkboxes.nth(4).locator('label'));
    await commonPage.addRightArrow(checkboxes.nth(5).locator('label'));
    await commonPage.addDownArrow(processesPage.migrateButton);

    await page.screenshot({
      path: path.join(baseDirectory, 'migrate-button.png'),
    });

    await commonPage.deleteArrows();

    await page.route(
      /^.*\/api.*$/i,
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

    await migrationMode.selectTargetProcess('Order process');

    await commonPage.addDownArrow(migrationMode.targetProcessDropdown);
    await commonPage.addDownArrow(migrationMode.targetVersionDropdown);

    await page.screenshot({
      path: path.join(baseDirectory, 'select-target-process.png'),
    });

    await commonPage.deleteArrows();

    await migrationMode.mapFlowNode({
      sourceFlowNodeName: 'Check payment',
      targetFlowNodeName: 'Check payment',
    });

    await migrationMode.mapFlowNode({
      sourceFlowNodeName: 'Ship Articles',
      targetFlowNodeName: 'Ship Articles',
    });

    await migrationMode.mapFlowNode({
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

    await migrationMode.selectTargetSourceFlowNode('Check payment');

    const flowNodes = page
      .getByTestId('diagram')
      .getByText('Check payment', {exact: true});

    await commonPage.addDownArrow(flowNodes.first());
    await commonPage.addDownArrow(flowNodes.nth(1));

    await page.screenshot({
      path: path.join(baseDirectory, 'highlight-mapping.png'),
    });

    await commonPage.deleteArrows();

    await migrationMode.nextButton.click();

    await commonPage.addUpArrow(page.getByTestId('state-overlay'));
    await commonPage.addUpArrow(page.getByTestId('modifications-overlay'));

    await page.screenshot({
      path: path.join(baseDirectory, 'summary.png'),
    });

    await commonPage.deleteArrows();

    await page.route(
      /^.*\/api.*$/i,
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

    await migrationMode.confirmButton.click();

    const taskBoundingBox = await page
      .getByTestId('diagram')
      .getByText('Ship Articles', {exact: true})
      .boundingBox();

    if (taskBoundingBox === null) {
      throw new Error(
        'An error occured when dragging the diagram: task bounding box is null',
      );
    }

    // move diagram into viewport to be fully visible
    await page.mouse.move(taskBoundingBox.x, taskBoundingBox.y - 50);
    await page.mouse.down();
    await page.mouse.move(taskBoundingBox.x - 200, taskBoundingBox.y - 50);
    await page.mouse.up();

    await expect(page.getByTestId('state-overlay')).toBeVisible();
    await expect(page.getByText(mockMigrationOperation.id)).toHaveCount(1);

    await page.screenshot({
      path: path.join(baseDirectory, 'operations-panel.png'),
    });
  });
});
