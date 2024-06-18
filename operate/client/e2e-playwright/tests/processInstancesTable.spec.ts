/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  test('Sorting', async ({page, processesPage: {filtersPanel}}) => {
    const {instances} = initialData;

    // pick one instance from each process
    const instanceIds = [
      instances.processA[0]!.processInstanceKey,
      instances.processB_v_1[0]!.processInstanceKey,
      instances.processB_v_2[0]!.processInstanceKey,
    ].sort();

    await filtersPanel.displayOptionalFilter('Process Instance Key(s)');

    await filtersPanel.processInstanceKeysFilter.type(instanceIds.join());

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

  test('Scrolling', async ({page, processesPage: {filtersPanel}}) => {
    const {instancesForInfiniteScroll} = initialData.instances;

    const descendingInstanceIds = instancesForInfiniteScroll
      .map((instance: any) => instance.processInstanceKey)
      .sort((instanceId1: number, instanceId2: number) => {
        return instanceId2 - instanceId1;
      });

    await filtersPanel.selectProcess('Process For Infinite Scroll');

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
