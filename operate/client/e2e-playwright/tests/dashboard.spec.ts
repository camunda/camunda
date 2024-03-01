/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {config} from '../config';
import {setup} from './dashboard.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {SETUP_WAITING_TIME} from './constants';

test.beforeAll(async ({request}) => {
  test.setTimeout(SETUP_WAITING_TIME);
  const {instanceIds, instanceWithAnIncident = ''} = await setup();
  // wait for instances and incident to be created
  await Promise.all([
    expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/api/process-instances`,
            {
              data: {
                query: {
                  active: true,
                  running: true,
                  incidents: true,
                  completed: true,
                  finished: true,
                  canceled: true,
                  ids: instanceIds,
                },
                pageSize: 50,
              },
            },
          );
          const instances = await response.json();
          return instances.totalCount;
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toEqual(38),
    expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/v1/incidents/search`,
            {
              data: {
                filter: {
                  processInstanceKey: parseInt(instanceWithAnIncident),
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

test.beforeEach(({dashboardPage}) => {
  dashboardPage.navigateToDashboard();
});

test.describe('Dashboard', () => {
  test('Statistics', async ({dashboardPage}) => {
    const incidentInstancesCount = Number(
      await dashboardPage.metricPanel
        .getByTestId('incident-instances-badge')
        .innerText(),
    );

    const activeProcessInstancesCount = Number(
      await dashboardPage.metricPanel
        .getByTestId('active-instances-badge')
        .innerText(),
    );

    const totalInstancesCount = await dashboardPage.metricPanel
      .getByTestId('total-instances-link')
      .innerText();

    await expect(totalInstancesCount).toBe(
      `${
        incidentInstancesCount + activeProcessInstancesCount
      } Running Process Instances in total`,
    );
  });

  test('Navigation to Processes View', async ({dashboardPage, page}) => {
    const activeProcessInstancesCount = await page
      .getByTestId('active-instances-badge')
      .nth(0)
      .innerText();

    const instancesWithIncidentCount = await page
      .getByTestId('incident-instances-badge')
      .nth(0)
      .innerText();

    await page.getByTestId('active-instances-link').click();

    await expect(
      page.getByRole('heading', {
        name: `Process Instances - ${activeProcessInstancesCount} result${
          Number(activeProcessInstancesCount) > 1 ? 's' : ''
        }`,
      }),
    ).toBeVisible();

    await dashboardPage.navigateToDashboard();

    await page.getByTestId('incident-instances-link').click();

    await expect(
      page.getByRole('heading', {
        name: `Process Instances - ${instancesWithIncidentCount} result${
          Number(instancesWithIncidentCount) > 1 ? 's' : ''
        }`,
      }),
    ).toBeVisible();
  });

  test('Select process instances by name', async ({page}) => {
    await expect(page.getByTestId('instances-by-process')).toBeVisible();

    const firstInstanceByProcess = page.getByTestId('instances-by-process-0');

    const incidentCount = Number(
      await firstInstanceByProcess
        .getByTestId('incident-instances-badge')
        .innerText(),
    );
    const runningInstanceCount = Number(
      await firstInstanceByProcess
        .getByTestId('active-instances-badge')
        .innerText(),
    );

    const totalInstanceCount = incidentCount + runningInstanceCount;

    await firstInstanceByProcess.click();

    await expect(
      page.getByRole('heading', {
        name: `Process Instances - ${totalInstanceCount} result${
          Number(totalInstanceCount) > 1 ? 's' : ''
        }`,
      }),
    ).toBeVisible();
  });

  test('Select process instances by error message', async ({page}) => {
    await expect(page.getByTestId('incident-byError')).toBeVisible();

    const firstInstanceByError = page.getByTestId('incident-byError-0');

    const incidentCount = await Number(
      await firstInstanceByError
        .getByTestId('incident-instances-badge')
        .innerText(),
    );

    await firstInstanceByError.click();

    await expect(
      page.getByRole('heading', {
        name: `Process Instances - ${incidentCount} result${
          Number(incidentCount) > 1 ? 's' : ''
        }`,
      }),
    ).toBeVisible();
  });

  test('Select process instances by error message (expanded)', async ({
    page,
  }) => {
    await expect(page.getByTestId('incident-byError')).toBeVisible();

    const firstInstanceByError = page.getByTestId('incident-byError-0');

    const incidentCount = await Number(
      await firstInstanceByError
        .getByTestId('incident-instances-badge')
        .innerText(),
    );

    await firstInstanceByError
      .getByRole('button', {
        name: 'Expand current row',
      })
      .click();

    await firstInstanceByError.getByRole('link').nth(0).click();

    await expect(
      page.getByRole('heading', {
        name: `Process Instances - ${incidentCount} result${
          Number(incidentCount) > 1 ? 's' : ''
        }`,
      }),
    ).toBeVisible();
  });
});
