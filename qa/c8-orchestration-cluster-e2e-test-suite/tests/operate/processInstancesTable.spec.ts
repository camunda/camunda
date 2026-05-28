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
import {navigateToApp} from '@pages/UtilitiesPage';
import {waitForAssertion} from 'utils/waitForAssertion';
import {sleep} from 'utils/sleep';
import {defaultAssertionOptions} from '../../utils/constants';

type ProcessInstance = {processInstanceKey: number};

let processA: ProcessInstance;
let processB_v_1: ProcessInstance;
let processB_v_2: ProcessInstance;
let scrollingInstances: ProcessInstance[];
const amountOfInstancesForInfiniteScroll = 350;

test.beforeAll(async () => {
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
});

test.describe('Process Instances Table', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
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
      // All three rows need the longer defaultAssertionOptions budget — the
      // nth(0) row was using Playwright's 5s default and flaked first.
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
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(0).innerText(),
        )
        .toContain(instanceIds[0].toString());
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(1).innerText(),
        )
        .toContain(instanceIds[1].toString());
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(2).innerText(),
        )
        .toContain(instanceIds[2].toString());
    });

    await test.step('Check sorting of processes by process version DESC', async () => {
      await operateProcessesPage.clickVersionSortButton();
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(0).innerText(),
        )
        .toContain(instanceIds[2].toString());
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(1).innerText(),
        )
        .toContain(instanceIds[0].toString());
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(2).innerText(),
        )
        .toContain(instanceIds[1].toString());
    });

    await test.step('Check sorting of processes by process version ASC', async () => {
      await operateProcessesPage.clickVersionSortButton();
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(0).innerText(),
        )
        .toContain(instanceIds[0].toString());
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(1).innerText(),
        )
        .toContain(instanceIds[1].toString());
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(2).innerText(),
        )
        .toContain(instanceIds[2].toString());
    });

    await test.step('Check sorting of processes by process name DESC', async () => {
      await operateProcessesPage.clickProcessNameSortButton();
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(0).innerText(),
        )
        .toContain('instancesTableProcessB');
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(1).innerText(),
        )
        .toContain('instancesTableProcessB');
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(2).innerText(),
        )
        .toContain('instancesTableProcessA');
    });

    await test.step('Check sorting of processes by process name ASC', async () => {
      await operateProcessesPage.clickProcessNameSortButton();
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(0).innerText(),
        )
        .toContain('instancesTableProcessA');
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(1).innerText(),
        )
        .toContain('instancesTableProcessB');
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(2).innerText(),
        )
        .toContain('instancesTableProcessB');
    });

    await test.step('Check sorting of processes by process instance key DESC', async () => {
      instanceIds.sort();
      await operateProcessesPage.clickProcessInstanceKeySortButton();
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(0).innerText(),
        )
        .toContain(instanceIds[2].toString());
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(1).innerText(),
        )
        .toContain(instanceIds[1].toString());
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(2).innerText(),
        )
        .toContain(instanceIds[0].toString());
    });

    await test.step('Check sorting of processes by process instance key ASC', async () => {
      await operateProcessesPage.clickProcessInstanceKeySortButton();
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(0).innerText(),
        )
        .toContain(instanceIds[0].toString());
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(1).innerText(),
        )
        .toContain(instanceIds[1].toString());
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(2).innerText(),
        )
        .toContain(instanceIds[2].toString());
    });
  });

  test('Scrolling of process instances', async ({
    page,
    operateProcessesPage,
    operateFiltersPanelPage,
  }) => {
    test.slow();
    const instanceRows = page.getByTestId('data-list').getByRole('row');

    // Reads the process-instance key directly from the dedicated cell of a
    // specific row. Avoids parsing the full row text and is resilient to any
    // number of total instances accumulated across beforeAll reruns on the same
    // Docker container (no data wipe between Playwright retries).
    const readKeyAt = async (rowIndex: number): Promise<string> =>
      (
        await instanceRows
          .nth(rowIndex)
          .getByTestId('cell-processInstanceKey')
          .innerText()
      ).trim();

    let key0 = '';  // topmost key after DESC sort; must stay at row 0 until windowing
    let key50 = ''; // 51st key; expected at row 0 after the 300-row window shift

    await test.step('Select process and sort by instance key DESC', async () => {
      await operateFiltersPanelPage.selectProcess(
        'Process For Infinite Scroll',
      );
      await operateFiltersPanelPage.selectVersion('1');
      // 350 instances can take longer than 10s to be indexed on a loaded
      // shared cluster, and waitForAssertion's default 3 reloads aren't
      // enough budget. Give each poll a longer timeout and more retries.
      //
      // Use >= comparison instead of exact text match: Playwright retries
      // re-run beforeAll (same Docker container, no data wipe), which adds
      // another 350 instances. The exact "350 results" text would never
      // match on a retry run — asserting >= 350 handles both cases.
      await waitForAssertion({
        assertion: async () => {
          await expect
            .poll(
              async () => {
                const text = await page.getByText(/\d+ results/).textContent();
                const match = text?.match(/(\d+) results/);
                return match ? parseInt(match[1], 10) : 0;
              },
              {timeout: 30000, intervals: [1000]},
            )
            .toBeGreaterThanOrEqual(amountOfInstancesForInfiniteScroll);
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 5,
      });
      await operateProcessesPage.clickProcessInstanceKeySortButton();
      await expect(instanceRows).toHaveCount(50);

      // Wait for the sort re-render to settle: poll until row 0's key is
      // stable across two consecutive reads (200 ms apart). Reading key0
      // immediately after toHaveCount can race against an in-progress
      // React re-sort and capture a transient value.
      await expect
        .poll(
          async () => {
            const k1 = await readKeyAt(0);
            await page.waitForTimeout(200);
            const k2 = await readKeyAt(0);
            return k1 === k2 && k1.length > 0;
          },
          {timeout: 10000, intervals: [300]},
        )
        .toBe(true);
      key0 = await readKeyAt(0);

      // Spot-check: first and last visible rows have populated key cells.
      await expect(
        instanceRows.nth(0).getByTestId('cell-processInstanceKey'),
      ).toBeVisible();
      await expect(
        instanceRows.nth(49).getByTestId('cell-processInstanceKey'),
      ).toBeVisible();
    });

    await test.step('Scroll to 100th instance', async () => {
      // Row index 50 is not in the DOM yet (only indices 0–49 are loaded).
      // scrollUntilElementIsVisible wheels down until it appears, triggering
      // the next batch.
      await operateProcessesPage.scrollUntilElementIsVisible(
        instanceRows.nth(50),
      );
      await expect(instanceRows).toHaveCount(100);
      key50 = await readKeyAt(50);
    });

    await test.step('Scroll to 150th instance', async () => {
      await operateProcessesPage.scrollUntilElementIsVisible(
        instanceRows.nth(100),
      );
      await expect(instanceRows).toHaveCount(150);
    });

    await test.step('Scroll to 200th instance', async () => {
      await operateProcessesPage.scrollUntilElementIsVisible(
        instanceRows.nth(150),
      );
      await sleep(500);
      await expect(instanceRows).toHaveCount(200);
      await expect
        .poll(() => instanceRows.nth(0).innerText())
        .toContain(key0);
      await expect(
        instanceRows.nth(199).getByTestId('cell-processInstanceKey'),
      ).toBeVisible();
    });

    await test.step('Scroll to 250th instance', async () => {
      await instanceRows.nth(199).scrollIntoViewIfNeeded();
      await sleep(500);
      await expect(instanceRows).toHaveCount(250);

      await expect
        .poll(() => instanceRows.nth(0).innerText())
        .toContain(key0);

      await expect(
        instanceRows.nth(249).getByTestId('cell-processInstanceKey'),
      ).toBeVisible();
    });

    await test.step('Scroll to 300th instance', async () => {
      await instanceRows.nth(249).scrollIntoViewIfNeeded();
      await sleep(500);
      // The virtual list evicts the first 50 rows when ~300 rows are loaded.
      // Exact count after windowing can vary slightly with batch boundaries
      // (e.g. 250–270); verify ≥ 250 and confirm window shift via key50.
      await expect
        .poll(() => instanceRows.count(), {timeout: 15000})
        .toBeGreaterThanOrEqual(250);

      // Row 0 must now hold key50 (the 51st item), confirming the oldest 50
      // rows were evicted from the virtual list.
      await expect
        .poll(() => instanceRows.nth(0).innerText(), {timeout: 15000})
        .toContain(key50);

      await expect(
        instanceRows.nth(249).getByTestId('cell-processInstanceKey'),
      ).toBeVisible();
    });
  });
});
