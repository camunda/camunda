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

import {setup} from './processInstancesTable.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {SETUP_WAITING_TIME_LONG} from './constants';
import {config} from '../config';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  initialData = await setup();
  test.setTimeout(SETUP_WAITING_TIME_LONG);

  await Promise.all([
    expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/v1/process-instances/search`,
            {
              data: {
                filter: {
                  bpmnProcessId: 'instancesTableProcessA',
                },
              },
            },
          );

          const instances: {total: number} = await response.json();
          return instances.total;
        },
        {timeout: SETUP_WAITING_TIME_LONG},
      )
      .toBe(30),
    expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/v1/process-instances/search`,
            {
              data: {
                filter: {
                  bpmnProcessId: 'instancesTableProcessB',
                },
              },
            },
          );

          const instances: {total: number} = await response.json();
          return instances.total;
        },
        {timeout: SETUP_WAITING_TIME_LONG},
      )
      .toBe(2),
    expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/v1/process-instances/search`,
            {
              data: {
                filter: {
                  bpmnProcessId: 'instancesTableProcessForInfiniteScroll',
                },
              },
            },
          );

          const instances: {total: number} = await response.json();
          return instances.total;
        },
        {timeout: SETUP_WAITING_TIME_LONG},
      )
      .toBe(300),
  ]);
});

test.beforeEach(async ({page, dashboardPage}) => {
  await dashboardPage.navigateToDashboard();
  await page.getByRole('link', {name: /processes/i}).click();
});

test.describe('Process Instances Table', () => {
  test('Sorting', async ({page, processesPage}) => {
    const {instances} = initialData;

    // pick one instance from each process
    const instanceIds = [
      instances.processA[0]!.processInstanceKey,
      instances.processB_v_1[0]!.processInstanceKey,
      instances.processB_v_2[0]!.processInstanceKey,
    ].sort();

    await processesPage.displayOptionalFilter('Process Instance Key(s)');

    await processesPage.processInstanceKeysFilter.type(instanceIds.join());

    await expect(page.getByText(/3 results/)).toBeVisible();

    const instanceRows = page.getByTestId('data-list').getByRole('row');

    // test default Start Date sorting
    await expect
      .poll(() => instanceRows.nth(0).innerText())
      .toContain(instanceIds[2]);
    await expect
      .poll(() => instanceRows.nth(1).innerText())
      .toContain(instanceIds[1]);
    await expect
      .poll(() => instanceRows.nth(2).innerText())
      .toContain(instanceIds[0]);

    await page
      .getByRole('button', {
        name: /sort by start date/i,
      })
      .click();

    await expect
      .poll(() => instanceRows.nth(0).innerText())
      .toContain(instanceIds[0]);
    await expect
      .poll(() => instanceRows.nth(1).innerText())
      .toContain(instanceIds[1]);
    await expect
      .poll(() => instanceRows.nth(2).innerText())
      .toContain(instanceIds[2]);

    await page
      .getByRole('button', {
        name: /sort by start date/i,
      })
      .click();

    await expect
      .poll(() => instanceRows.nth(0).innerText())
      .toContain(instanceIds[2]);
    await expect
      .poll(() => instanceRows.nth(1).innerText())
      .toContain(instanceIds[1]);
    await expect
      .poll(() => instanceRows.nth(2).innerText())
      .toContain(instanceIds[0]);

    await page
      .getByRole('button', {
        name: /sort by process instance key/i,
      })
      .click();

    await expect
      .poll(() => instanceRows.nth(0).innerText())
      .toContain(instanceIds[2]);
    await expect
      .poll(() => instanceRows.nth(1).innerText())
      .toContain(instanceIds[1]);
    await expect
      .poll(() => instanceRows.nth(2).innerText())
      .toContain(instanceIds[0]);

    await page
      .getByRole('button', {
        name: /sort by process instance key/i,
      })
      .click();

    await expect
      .poll(() => instanceRows.nth(0).innerText())
      .toContain(instanceIds[0]);
    await expect
      .poll(() => instanceRows.nth(1).innerText())
      .toContain(instanceIds[1]);
    await expect
      .poll(() => instanceRows.nth(2).innerText())
      .toContain(instanceIds[2]);

    await page
      .getByRole('button', {
        name: /sort by version/i,
      })
      .click();

    await expect
      .poll(() => instanceRows.nth(0).innerText())
      .toContain(instanceIds[2]);
    await expect
      .poll(() => instanceRows.nth(1).innerText())
      .toContain(instanceIds[0]);
    await expect
      .poll(() => instanceRows.nth(2).innerText())
      .toContain(instanceIds[1]);

    await page
      .getByRole('button', {
        name: /sort by version/i,
      })
      .click();

    await expect
      .poll(() => instanceRows.nth(0).innerText())
      .toContain(instanceIds[0]);
    await expect
      .poll(() => instanceRows.nth(1).innerText())
      .toContain(instanceIds[1]);
    await expect
      .poll(() => instanceRows.nth(2).innerText())
      .toContain(instanceIds[2]);

    await page
      .getByRole('button', {
        name: /sort by name/i,
      })
      .click();

    await expect
      .poll(() => instanceRows.nth(0).innerText())
      .toContain('instancesTableProcessB');
    await expect
      .poll(() => instanceRows.nth(1).innerText())
      .toContain('instancesTableProcessB');
    await expect
      .poll(() => instanceRows.nth(2).innerText())
      .toContain('instancesTableProcessA');

    await page
      .getByRole('button', {
        name: /sort by name/i,
      })
      .click();

    await expect
      .poll(() => instanceRows.nth(0).innerText())
      .toContain('instancesTableProcessA');
    await expect
      .poll(() => instanceRows.nth(1).innerText())
      .toContain('instancesTableProcessB');
    await expect
      .poll(() => instanceRows.nth(2).innerText())
      .toContain('instancesTableProcessB');
  });

  test('Scrolling', async ({page, processesPage}) => {
    const {instancesForInfiniteScroll} = initialData.instances;

    const descendingInstanceIds = instancesForInfiniteScroll
      .map((instance: any) => instance.processInstanceKey)
      .sort((instanceId1: number, instanceId2: number) => {
        return instanceId2 - instanceId1;
      });

    await processesPage.selectProcess('Process For Infinite Scroll');

    await page
      .getByRole('button', {
        name: /sort by process instance key/i,
      })
      .click();

    const instanceRows = page.getByTestId('data-list').getByRole('row');

    await expect(instanceRows).toHaveCount(50);

    await expect
      .poll(() => instanceRows.nth(0).innerText())
      .toContain(descendingInstanceIds[0]);
    await expect
      .poll(() => instanceRows.nth(49).innerText())
      .toContain(descendingInstanceIds[49]);

    // scroll until max stored instances is reached (200)
    await page
      .getByRole('row', {name: `Instance ${descendingInstanceIds[49]}`})
      .scrollIntoViewIfNeeded();

    await expect(instanceRows).toHaveCount(100);

    await page
      .getByRole('row', {name: `Instance ${descendingInstanceIds[99]}`})
      .scrollIntoViewIfNeeded();
    await expect(instanceRows).toHaveCount(150);

    await page
      .getByRole('row', {name: `Instance ${descendingInstanceIds[149]}`})
      .scrollIntoViewIfNeeded();
    await expect(instanceRows).toHaveCount(200);

    await expect
      .poll(() => instanceRows.nth(0).innerText())
      .toContain(descendingInstanceIds[0]);
    await expect
      .poll(() => instanceRows.nth(199).innerText())
      .toContain(descendingInstanceIds[199]);

    await page
      .getByRole('row', {name: `Instance ${descendingInstanceIds[199]}`})
      .scrollIntoViewIfNeeded();

    await expect(instanceRows).toHaveCount(200);

    await expect
      .poll(() => instanceRows.nth(0).innerText())
      .toContain(descendingInstanceIds[50]);
    await expect
      .poll(() => instanceRows.nth(199).innerText())
      .toContain(descendingInstanceIds[249]);

    await page
      .getByRole('row', {name: `Instance ${descendingInstanceIds[50]}`})
      .scrollIntoViewIfNeeded();

    await expect(instanceRows).toHaveCount(200);

    await expect
      .poll(() => instanceRows.nth(0).innerText())
      .toContain(descendingInstanceIds[0]);
    await expect
      .poll(() => instanceRows.nth(199).innerText())
      .toContain(descendingInstanceIds[199]);
  });
});
