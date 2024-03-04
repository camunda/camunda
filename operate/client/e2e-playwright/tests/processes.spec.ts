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

import {setup} from './processes.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {convertToQueryString} from '../utils/convertToQueryString';
import {deployProcess} from '../setup-utils';
import {config} from '../config';
import {SETUP_WAITING_TIME} from './constants';
import {Paths} from 'modules/Routes';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  initialData = await setup();
  test.setTimeout(SETUP_WAITING_TIME);

  await Promise.all([
    expect
      .poll(
        async () => {
          const response = await request.get(
            `${config.endpoint}/v1/process-instances/${initialData.instanceWithoutAnIncident.processInstanceKey}`,
          );

          return response.status();
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(200),
    expect
      .poll(
        async () => {
          const response = await request.get(
            `${config.endpoint}/v1/process-instances/${initialData.instanceWithAnIncident.processInstanceKey}`,
          );

          return response.status();
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(200),
    expect
      .poll(
        async () => {
          const response = await request.get(
            `${config.endpoint}/v1/process-instances/${initialData.instanceToCancel.processInstanceKey}`,
          );

          return response.status();
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(200),
    expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/v1/incidents/search`,
            {
              data: {
                filter: {
                  processInstanceKey: parseInt(
                    initialData.instanceWithAnIncident.processInstanceKey,
                  ),
                },
              },
            },
          );

          const incidents: {items: [{state: string}]; total: number} =
            await response.json();

          return (
            incidents.total > 0 &&
            incidents.items.filter(({state}) => state === 'PENDING').length ===
              0
          );
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBeTruthy(),
  ]);
});

test.beforeEach(async ({page, dashboardPage}) => {
  await dashboardPage.navigateToDashboard();
  await page.getByRole('link', {name: /processes/i}).click();
});

test.describe('Processes', () => {
  test('Processes Page Initial Load', async ({processesPage, page}) => {
    await processesPage.validateCheckedState({
      checked: [
        processesPage.runningInstancesCheckbox,
        processesPage.activeCheckbox,
        processesPage.incidentsCheckbox,
      ],
      unChecked: [
        processesPage.finishedInstancesCheckbox,
        processesPage.completedCheckbox,
        processesPage.canceledCheckbox,
      ],
    });

    await expect(page.getByText('There is no Process selected')).toBeVisible();
    await expect(
      page.getByText('To see a Diagram, select a Process in the Filters panel'),
    ).toBeVisible();

    await processesPage.displayOptionalFilter('Process Instance Key(s)');

    await processesPage.processInstanceKeysFilter.fill(
      `${initialData.instanceWithoutAnIncident.processInstanceKey}, ${initialData.instanceWithAnIncident.processInstanceKey}`,
    );

    const table = page.getByRole('table');

    await expect(table).toBeVisible();
    await expect(table.getByRole('row')).toHaveCount(3);

    await expect(
      table.getByTestId(
        `INCIDENT-icon-${initialData.instanceWithAnIncident.processInstanceKey}`,
      ),
    ).toBeVisible();

    await expect(
      table.getByTestId(
        `ACTIVE-icon-${initialData.instanceWithoutAnIncident.processInstanceKey}`,
      ),
    ).toBeVisible();
  });

  test('Select flow node in diagram', async ({processesPage, page}) => {
    const instance = initialData.instanceWithoutAnIncident;

    await processesPage.displayOptionalFilter('Process Instance Key(s)');

    // Filter by Process Instance Key
    await processesPage.processInstanceKeysFilter.fill(
      instance.processInstanceKey,
    );

    await expect(page.getByTestId('diagram')).not.toBeInViewport();

    await processesPage.selectProcess('Order process');

    // Select "Ship Articles" flow node
    const shipArticlesTaskId = 'shipArticles';
    await expect(page.getByTestId('diagram')).toBeInViewport();

    await processesPage.diagram.clickFlowNode('Ship Articles');
    await expect(processesPage.flowNodeFilter).toHaveValue('Ship Articles');

    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).toBeVisible();

    await expect(page).toHaveURL(
      `${Paths.processes()}?${convertToQueryString({
        active: 'true',
        incidents: 'true',
        ids: instance.processInstanceKey,
        process: 'orderProcess',
        version: '1',
        flowNodeId: shipArticlesTaskId,
      })}`,
    );

    // Select "Check Payment" flow node
    const checkPaymentTaskId = 'checkPayment';

    await processesPage.diagram.clickFlowNode('Check payment');
    await expect(processesPage.flowNodeFilter).toHaveValue('Check payment');

    await expect(page.getByRole('table').getByRole('row')).toHaveCount(2);

    await expect(page).toHaveURL(
      `${Paths.processes()}?${convertToQueryString({
        active: 'true',
        incidents: 'true',
        ids: instance.processInstanceKey,
        process: 'orderProcess',
        version: '1',
        flowNodeId: checkPaymentTaskId,
      })}`,
    );
  });

  test('Wait for process creation', async ({processesPage, page}) => {
    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        incidents: 'true',
        process: 'testProcess',
        version: '1',
      },
    });

    await expect(page.getByTestId('data-table-skeleton')).toBeVisible();
    await expect(page.getByTestId('diagram-spinner')).toBeVisible();

    await expect(processesPage.processNameFilter).toBeDisabled();

    await deployProcess(['newProcess.bpmn']);

    await expect(page.getByTestId('diagram')).toBeInViewport({timeout: 20000});

    await expect(page.getByTestId('data-table-skeleton')).not.toBeVisible();
    await expect(page.getByTestId('diagram-spinner')).not.toBeVisible();

    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).toBeVisible();

    await expect(processesPage.processNameFilter).toBeEnabled();
    await expect(processesPage.processNameFilter).toHaveValue('Test Process');
  });

  test('Delete process definition after canceling running instance', async ({
    processesPage,
    page,
  }) => {
    await processesPage.navigateToProcesses({
      searchParams: {
        active: 'true',
        incidents: 'true',
        process: 'processToDelete',
        version: '1',
      },
    });

    await expect(page.getByTestId('data-table-skeleton')).not.toBeVisible();
    await expect(page.getByTestId('diagram-spinner')).not.toBeVisible();

    await expect(
      page.getByRole('heading', {
        name: /process instances - 1 result/i,
      }),
    ).toBeVisible();

    await expect(
      page.getByRole('button', {
        name: 'Only process definitions without running instances can be deleted.',
      }),
    ).toBeDisabled();

    await page
      .getByRole('button', {
        name: /cancel instance/i,
      })
      .click();

    await page
      .getByRole('button', {
        name: 'Apply',
      })
      .click();

    await expect(
      page.getByText('There are no Instances matching this filter set'),
    ).toBeVisible();

    await expect(processesPage.deleteResourceButton).toBeEnabled();

    await processesPage.deleteResourceButton.click();
    await expect(
      processesPage.deleteResourceModal.confirmCheckbox,
    ).toBeVisible();

    await processesPage.deleteResourceModal.confirmCheckbox.click({
      force: true,
    });
    await processesPage.deleteResourceModal.confirmButton.click();

    await expect
      .poll(
        async () => {
          const response = await page.request.post(
            `${config.endpoint}/v1/process-definitions/search`,
            {
              data: {
                filter: {
                  bpmnProcessId: 'processToDelete',
                },
              },
            },
          );

          const definitions: {total: number} = await response.json();
          return definitions.total;
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(0);
  });
});
