/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy, createInstances, createSingleInstance} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToAppHome} from '@pages/UtilitiesPage';
import {waitForAssertion} from 'utils/waitForAssertion';
import {sleep} from 'utils/sleep';
import {defaultAssertionOptions} from '../../utils/constants';
import {jsonHeaders} from 'utils/http';

type ProcessInstance = {processInstanceKey: number};

let processA: ProcessInstance;
let processB_v_1: ProcessInstance;
let processB_v_2: ProcessInstance;
let scrollingInstances: ProcessInstance[];
const amountOfInstancesForInfiniteScroll = 350;

test.beforeAll(async ({request}) => {
  test.setTimeout(180_000);

  await deploy([
    './resources/instancesTableProcessA.bpmn',
    './resources/instancesTableProcessB_v_1.bpmn',
    './resources/instancesTableProcessForInfiniteScroll.bpmn',
  ]);
  processA = {
    processInstanceKey: Number(
      (await createSingleInstance('instancesTableProcessA', 1))
        .processInstanceKey,
    ),
  };
  await sleep(500);
  {
    processB_v_1 = {
      processInstanceKey: Number(
        (await createSingleInstance('instancesTableProcessB', 1))
          .processInstanceKey,
      ),
    };
    await deploy(['./resources/instancesTableProcessB_v_2.bpmn']);
    await sleep(1000);
    processB_v_2 = {
      processInstanceKey: Number(
        (await createSingleInstance('instancesTableProcessB', 2))
          .processInstanceKey,
      ),
    };
  }
  const createdInstances = await createInstances(
    'instancesTableProcessForInfiniteScroll',
    1,
    amountOfInstancesForInfiniteScroll,
  );
  scrollingInstances = createdInstances.map((instance) => ({
    processInstanceKey: Number(instance.processInstanceKey),
  }));

  // Wait until the LAST created instance is indexed by Operate before any
  // test runs. The scrolling test asserts "350 results" with a short retry
  // budget and would otherwise race against the exporter/importer pipeline
  // on slow runners.
  //
  // Filter by processInstanceKey rather than processDefinitionId: the latter
  // returns one record per partition for the same instance on multi-partition
  // clusters (see https://github.com/camunda/camunda/issues/52778 for the
  // analogous bug in the MCP Processes search). Instances are created
  // sequentially, so once the last one is indexed, the earlier ones are too.
  const lastInstanceKey =
    scrollingInstances[
      scrollingInstances.length - 1
    ].processInstanceKey.toString();
  await expect
    .poll(
      async () => {
        const response = await request.post('/v2/process-instances/search', {
          headers: jsonHeaders(),
          data: {filter: {processInstanceKey: lastInstanceKey}},
        });
        if (response.status() !== 200) {
          // Surface the real failure (e.g. 401 auth misconfig, 503 transient
          // gateway error) instead of letting the poll time out with a
          // misleading "expected > 0".
          throw new Error(
            `process-instances/search returned ${response.status()}: ${await response.text()}`,
          );
        }
        const result = await response.json();
        return result.page?.totalItems ?? 0;
      },
      {timeout: 120_000, intervals: [2_000, 5_000, 10_000]},
    )
    .toBeGreaterThan(0);
});

test.describe('Process Instances Table', () => {
  test.beforeEach(async ({page, operateHomePage}) => {
    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Sorting of process instances', async ({
    page,
    operateProcessesPage,
    operateFiltersPanelPage,
  }) => {
    test.slow();
    let instanceIds: number[] = [
      processA.processInstanceKey,
      processB_v_1.processInstanceKey,
      processB_v_2.processInstanceKey,
    ];

    await test.step('Extract process instance keys and filter by the extracted values', async () => {
      await operateFiltersPanelPage.displayOptionalFilter(
        'Process Instance Key(s)',
      );
      await operateFiltersPanelPage.processInstanceKeysFilter.fill(
        instanceIds.join(),
      );
      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('3 results')).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });

    await test.step('Check default sorting of processes', async () => {
      // All three polls share the same source of flake (table re-renders
      // while default sort settles) — use the same timeout budget for the
      // first row check as the next two, otherwise it races at the default
      // 5s timeout while the page is still sorting.
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(0).innerText(),
          defaultAssertionOptions,
        )
        .toContain(instanceIds[2].toString());
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(1).innerText(),
          defaultAssertionOptions,
        )
        .toContain(instanceIds[1].toString());
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(2).innerText(),
          defaultAssertionOptions,
        )
        .toContain(instanceIds[0].toString());
    });

    await test.step('Check sorting of processes by start date ASC', async () => {
      await operateProcessesPage.clickStartDateSortButton();
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(0).innerText(),
          defaultAssertionOptions,
        )
        .toContain(instanceIds[0].toString());
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(1).innerText(),
          defaultAssertionOptions,
        )
        .toContain(instanceIds[1].toString());
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(2).innerText(),
          defaultAssertionOptions,
        )
        .toContain(instanceIds[2].toString());
    });

    await test.step('Check sorting of processes by process version DESC', async () => {
      await operateProcessesPage.clickVersionSortButton();
      // The two v1 instances tie on version, and Operate's tie-break is
      // environment-dependent (start date DESC when timestamps differ;
      // process-instance-key when they don't). Only assert what the test
      // is actually verifying — the primary sort: v2 at the top, both v1
      // instances below in any order.
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(0).innerText(),
          defaultAssertionOptions,
        )
        .toContain(instanceIds[2].toString());
      await expect
        .poll(async () => {
          const row1 = await operateProcessesPage.processInstancesTable
            .nth(1)
            .innerText();
          const row2 = await operateProcessesPage.processInstancesTable
            .nth(2)
            .innerText();
          const combined = `${row1}||${row2}`;
          return (
            combined.includes(instanceIds[0].toString()) &&
            combined.includes(instanceIds[1].toString())
          );
        }, defaultAssertionOptions)
        .toBe(true);
    });

    await test.step('Check sorting of processes by process version ASC', async () => {
      await operateProcessesPage.clickVersionSortButton();
      // Mirror of the DESC check: both v1 instances at the top in any
      // order, v2 at the bottom.
      await expect
        .poll(async () => {
          const row0 = await operateProcessesPage.processInstancesTable
            .nth(0)
            .innerText();
          const row1 = await operateProcessesPage.processInstancesTable
            .nth(1)
            .innerText();
          const combined = `${row0}||${row1}`;
          return (
            combined.includes(instanceIds[0].toString()) &&
            combined.includes(instanceIds[1].toString())
          );
        }, defaultAssertionOptions)
        .toBe(true);
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(2).innerText(),
          defaultAssertionOptions,
        )
        .toContain(instanceIds[2].toString());
    });

    await test.step('Check sorting of processes by process name DESC', async () => {
      await operateProcessesPage.clickProcessNameSortButton();
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(0).innerText(),
          defaultAssertionOptions,
        )
        .toContain('instancesTableProcessB');
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(1).innerText(),
          defaultAssertionOptions,
        )
        .toContain('instancesTableProcessB');
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(2).innerText(),
          defaultAssertionOptions,
        )
        .toContain('instancesTableProcessA');
    });

    await test.step('Check sorting of processes by process name ASC', async () => {
      await operateProcessesPage.clickProcessNameSortButton();
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(0).innerText(),
          defaultAssertionOptions,
        )
        .toContain('instancesTableProcessA');
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(1).innerText(),
          defaultAssertionOptions,
        )
        .toContain('instancesTableProcessB');
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(2).innerText(),
          defaultAssertionOptions,
        )
        .toContain('instancesTableProcessB');
    });

    await test.step('Check sorting of processes by process instance key DESC', async () => {
      instanceIds.sort();
      await operateProcessesPage.clickProcessInstanceKeySortButton();
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(0).innerText(),
          defaultAssertionOptions,
        )
        .toContain(instanceIds[2].toString());
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(1).innerText(),
          defaultAssertionOptions,
        )
        .toContain(instanceIds[1].toString());
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(2).innerText(),
          defaultAssertionOptions,
        )
        .toContain(instanceIds[0].toString());
    });

    await test.step('Check sorting of processes by process instance key ASC', async () => {
      await operateProcessesPage.clickProcessInstanceKeySortButton();
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(0).innerText(),
          defaultAssertionOptions,
        )
        .toContain(instanceIds[0].toString());
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(1).innerText(),
          defaultAssertionOptions,
        )
        .toContain(instanceIds[1].toString());
      await expect
        .poll(
          () => operateProcessesPage.processInstancesTable.nth(2).innerText(),
          defaultAssertionOptions,
        )
        .toContain(instanceIds[2].toString());
    });
  });

  // skipped due to bug 52804: https://github.com/camunda/camunda/issues/52804
  // On multi-partition clusters, the Process Instances page count is
  // multiplied by partition count (e.g. 350 × 3 = 1050), so the hardcoded
  // "350 results" check is unreachable. The test is also racey on slow
  // single-partition runners where some instances complete before all 350
  // are indexed, dropping the count below 350. Re-enable once #52804 is
  // fixed and the test is rewritten to not hardcode the count.
  test.skip('Scrolling of process instances', async ({
    page,
    operateProcessesPage,
    operateFiltersPanelPage,
  }) => {
    test.slow();
    const descendingInstanceIds = [...scrollingInstances]
      .map((instance) => instance.processInstanceKey)
      .sort((a, b) => b - a);
    const instanceRows = page.getByTestId('data-list').getByRole('row');

    await test.step('Select process and sort by instance key DESC', async () => {
      await operateFiltersPanelPage.selectProcess(
        'Process For Infinite Scroll',
      );
      await operateFiltersPanelPage.selectVersion('1');
      await waitForAssertion({
        assertion: async () => {
          await expect(
            page.getByText(`${amountOfInstancesForInfiniteScroll} results`),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
      });
      await operateProcessesPage.clickProcessInstanceKeySortButton();
      await expect(instanceRows).toHaveCount(50);
      await expect
        .poll(() => instanceRows.nth(0).innerText())
        .toContain(descendingInstanceIds[0].toString());
      await expect
        .poll(() => instanceRows.nth(49).innerText())
        .toContain(descendingInstanceIds[49].toString());
    });

    await test.step('Scroll to 100th instance', async () => {
      await operateProcessesPage.scrollUntilElementIsVisible(
        page.getByRole('row', {name: `Instance ${descendingInstanceIds[50]}`}),
      );

      await expect(instanceRows).toHaveCount(100);
    });

    await test.step('Scroll to 150th instance', async () => {
      await operateProcessesPage.scrollUntilElementIsVisible(
        page.getByRole('row', {name: `Instance ${descendingInstanceIds[100]}`}),
      );
      await expect(instanceRows).toHaveCount(150);
    });

    await test.step('Scroll to 200th instance', async () => {
      await operateProcessesPage.scrollUntilElementIsVisible(
        page.getByRole('row', {name: `Instance ${descendingInstanceIds[150]}`}),
      );
      await sleep(500);
      await expect(instanceRows).toHaveCount(200);
      await expect
        .poll(() => instanceRows.nth(0).innerText())
        .toContain(descendingInstanceIds[0].toString());
      await expect
        .poll(() => instanceRows.nth(199).innerText())
        .toContain(descendingInstanceIds[199].toString());
    });

    await test.step('Scroll to 250th instance', async () => {
      await page
        .getByRole('row', {name: `Instance ${descendingInstanceIds[199]}`})
        .scrollIntoViewIfNeeded();
      await sleep(500);
      await expect(instanceRows).toHaveCount(250);

      await expect
        .poll(() => instanceRows.nth(0).innerText())
        .toContain(descendingInstanceIds[0].toString());

      await expect
        .poll(() => instanceRows.nth(249).innerText())
        .toContain(descendingInstanceIds[249].toString());
    });

    await test.step('Scroll to 300th instance', async () => {
      await page
        .getByRole('row', {name: `Instance ${descendingInstanceIds[249]}`})
        .scrollIntoViewIfNeeded();
      await sleep(500);
      await expect(instanceRows).toHaveCount(250);

      await expect
        .poll(() => instanceRows.nth(0).innerText())
        .toContain(descendingInstanceIds[50].toString());

      await expect
        .poll(() => instanceRows.nth(249).innerText())
        .toContain(descendingInstanceIds[299].toString());
    });
  });
});
