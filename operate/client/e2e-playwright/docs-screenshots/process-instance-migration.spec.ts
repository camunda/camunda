/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
    migrationView,
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

    await migrationView.selectTargetProcess('Order process');

    await commonPage.addDownArrow(migrationView.targetProcessDropdown);
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

    await migrationView.confirmButton.click();

    await processesPage.diagram.moveCanvasHorizontally(-200);

    await expect(page.getByTestId('state-overlay')).toBeVisible();
    await expect(page.getByText(mockMigrationOperation.id)).toHaveCount(1);

    await page.screenshot({
      path: path.join(baseDirectory, 'operations-panel.png'),
    });
  });
});
