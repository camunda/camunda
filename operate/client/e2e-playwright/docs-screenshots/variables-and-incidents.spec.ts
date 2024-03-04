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
import {expect} from '@playwright/test';

import {mockResponses as mockProcessesResponses} from '../mocks/processes.mocks';
import {
  mockResponses as mockProcessDetailResponses,
  orderProcessInstance,
} from '../mocks/processInstance.mocks';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('variables and incidents', () => {
  test('view process with incident', async ({page, processesPage}) => {
    await page.route(
      /^.*\/api.*$/i,
      mockProcessesResponses({
        groupedProcesses: [
          {
            bpmnProcessId: 'order-process',
            name: null,
            permissions: [],
            processes: [
              {
                id: '2251799813686456',
                name: 'order-process',
                version: 1,
                bpmnProcessId: 'order-process',
              },
            ],
            tenantId: '<default>',
          },
        ],
        batchOperations: [],
        processInstances: {
          processInstances: [
            {
              id: '2251799813725328',
              processId: '2251799813688192',
              processName: 'order-process',
              processVersion: 2,
              startDate: '2023-09-29T07:16:22.701+0000',
              endDate: null,
              state: 'INCIDENT',
              bpmnProcessId: 'order-process',
              hasActiveOperation: false,
              operations: [],
              parentInstanceId: null,
              rootInstanceId: null,
              callHierarchy: [],
              sortValues: [],
              permissions: [],
              tenantId: '<default>',
            },
          ],
          totalCount: 1,
        },
        statistics: [
          {
            activityId: 'Gateway_1qlqb7o',
            active: 0,
            canceled: 0,
            incidents: 1,
            completed: 0,
          },
        ],
        processXml: orderProcessInstance.incidentState.xml,
      }),
    );

    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        incidents: 'true',
        process: 'order-process',
        version: '1',
      },
      options: {waitUntil: 'networkidle'},
    });

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-process-view-incident.png',
    });
  });

  test('resolve an incident', async ({
    page,
    commonPage,
    processInstancePage,
  }) => {
    await page.route(
      /^.*\/api.*$/i,
      mockProcessDetailResponses({
        processInstanceDetail: orderProcessInstance.incidentState.detail,
        flowNodeInstances: orderProcessInstance.incidentState.flowNodeInstances,
        statistics: orderProcessInstance.incidentState.statistics,
        sequenceFlows: orderProcessInstance.incidentState.sequenceFlows,
        variables: orderProcessInstance.incidentState.variables,
        incidents: orderProcessInstance.incidentState.incidents,
        xml: orderProcessInstance.incidentState.xml,
      }),
    );

    await processInstancePage.navigateToProcessInstance({
      id: '2251799813725328',
      options: {waitUntil: 'networkidle'},
    });

    await expect(
      page.getByRole('button', {
        name: /view 1 incident in instance/i,
      }),
    ).toBeVisible();

    await page
      .getByRole('button', {
        name: /view 1 incident in instance/i,
      })
      .click();

    await expect(
      page.getByRole('combobox', {
        name: /filter by flow node/i,
      }),
    ).toBeInViewport();
    await expect(
      page.getByRole('combobox', {
        name: /filter by incident type/i,
      }),
    ).toBeInViewport();

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-view-instance-incident.png',
    });

    const editVariableButton = await page.getByRole('button', {
      name: 'Edit variable orderValue',
    });

    await commonPage.addUpArrow(editVariableButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-view-instance-edit-icon.png',
    });

    await commonPage.deleteArrows();

    await editVariableButton.click();

    const editableField = page.getByRole('textbox', {
      name: /value/i,
    });
    await editableField.clear();
    await editableField.fill('99');

    const saveVariableButton = await page.getByRole('button', {
      name: 'Save variable',
    });

    await expect(saveVariableButton).toBeEnabled();

    await commonPage.addUpArrow(saveVariableButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-view-instance-save-variable-icon.png',
    });
  });

  test('retry an incident', async ({page, commonPage, processInstancePage}) => {
    await page.route(
      /^.*\/api.*$/i,
      mockProcessDetailResponses({
        processInstanceDetail: orderProcessInstance.incidentState.detail,
        flowNodeInstances: orderProcessInstance.incidentState.flowNodeInstances,
        statistics: orderProcessInstance.incidentState.statistics,
        sequenceFlows: orderProcessInstance.incidentState.sequenceFlows,
        variables: orderProcessInstance.incidentResolvedState.variables,
        incidents: orderProcessInstance.incidentState.incidents,
        xml: orderProcessInstance.incidentState.xml,
      }),
    );

    await processInstancePage.navigateToProcessInstance({
      id: '2251799813725328',
      options: {waitUntil: 'networkidle'},
    });

    await expect(
      page.getByRole('button', {
        name: /view 1 incident in instance/i,
      }),
    ).toBeVisible();

    await page
      .getByRole('button', {
        name: /view 1 incident in instance/i,
      })
      .click();

    await expect(
      page.getByRole('combobox', {
        name: /filter by flow node/i,
      }),
    ).toBeInViewport();
    await expect(
      page.getByRole('combobox', {
        name: /filter by incident type/i,
      }),
    ).toBeInViewport();

    const retryIncidentButton = await page.getByRole('button', {
      name: 'Retry Incident',
    });

    await commonPage.addRightArrow(retryIncidentButton);

    const retryInstanceButton = await page.getByRole('button', {
      name: /Retry Instance/,
    });

    await commonPage.addRightArrow(retryInstanceButton);

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-process-retry-incident.png',
    });
  });

  test('view resolved incident', async ({page, processInstancePage}) => {
    await page.route(
      /^.*\/api.*$/i,
      mockProcessDetailResponses({
        processInstanceDetail:
          orderProcessInstance.incidentResolvedState.detail,
        flowNodeInstances:
          orderProcessInstance.incidentResolvedState.flowNodeInstances,
        statistics: orderProcessInstance.incidentResolvedState.statistics,
        sequenceFlows: orderProcessInstance.incidentResolvedState.sequenceFlows,
        variables: orderProcessInstance.incidentResolvedState.variables,
        xml: orderProcessInstance.incidentResolvedState.xml,
      }),
    );

    await processInstancePage.navigateToProcessInstance({
      id: '2251799813725328',
      options: {waitUntil: 'networkidle'},
    });

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-incident-resolved.png',
    });
  });

  test('view completed instance', async ({page, processInstancePage}) => {
    await page.route(
      /^.*\/api.*$/i,
      mockProcessDetailResponses({
        processInstanceDetail: orderProcessInstance.completedState.detail,
        flowNodeInstances:
          orderProcessInstance.completedState.flowNodeInstances,
        statistics: orderProcessInstance.completedState.statistics,
        sequenceFlows: orderProcessInstance.completedState.sequenceFlows,
        variables: orderProcessInstance.completedState.variables,
        xml: orderProcessInstance.completedState.xml,
      }),
    );

    await processInstancePage.navigateToProcessInstance({
      id: '2251799813725328',
      options: {waitUntil: 'networkidle'},
    });

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/variables-and-incidents/operate-incident-resolved-path.png',
    });
  });
});
