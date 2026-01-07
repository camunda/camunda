/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy, createSingleInstance} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {waitForAssertion} from 'utils/waitForAssertion';
import {sleep} from 'utils/sleep';

type ProcessInstance = {processInstanceKey: number};

let processA: ProcessInstance;
let processB_v_1: ProcessInstance;
let processB_v_2: ProcessInstance;
let scrollingInstances: ProcessInstance[];

test.beforeAll(async () => {
  await deploy([
    './resources/instancesTableProcessA.bpmn',
    './resources/instancesTableProcessB_v_1.bpmn',
    './resources/instancesTableProcessForInfiniteScroll.bpmn',
  ]);

  processA = {
    processInstanceKey: Number(
      (await createSingleInstance('instancesTableProcessA')).processInstanceKey,
    ),
  };
  await sleep(500);

  processB_v_1 = {
    processInstanceKey: Number(
      (await createSingleInstance('instancesTableProcessB')).processInstanceKey,
    ),
  };

  await deploy(['./resources/instancesTableProcessB_v_2.bpmn']);
  await sleep(1000);

  processB_v_2 = {
    processInstanceKey: Number(
      (await createSingleInstance('instancesTableProcessB')).processInstanceKey,
    ),
  };

  // Create scrolling instances
  const createdInstances = [];
  for (let i = 0; i < 300; i++) {
    const instance = await createSingleInstance(
      'instancesTableProcessForInfiniteScroll',
    );
    createdInstances.push(instance);
  }
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
    const descendingInstanceIds = [...scrollingInstances]
      .map((instance) => instance.processInstanceKey)
      .sort((a, b) => b - a);
    const instanceRows = page.getByTestId('data-list').getByRole('row');

    await test.step('Select process and sort by instance key DESC', async () => {
      await operateFiltersPanelPage.selectProcess(
        'Process For Infinite Scroll',
      );
      await waitForAssertion({
        assertion: async () => {
          await expect(page.getByText('300 results')).toBeVisible();
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
      await page
        .getByRole('row', {name: `Instance ${descendingInstanceIds[49]}`})
        .scrollIntoViewIfNeeded();

      await expect(instanceRows).toHaveCount(100);
    });

    await test.step('Scroll to 150th instance', async () => {
      await page
        .getByRole('row', {name: `Instance ${descendingInstanceIds[99]}`})
        .scrollIntoViewIfNeeded();
      await expect(instanceRows).toHaveCount(150);
    });

    await test.step('Scroll to 200th instance', async () => {
      await page
        .getByRole('row', {name: `Instance ${descendingInstanceIds[149]}`})
        .scrollIntoViewIfNeeded();
      await sleep(500);
      await expect(instanceRows).toHaveCount(200);
      await expect
        .poll(() => instanceRows.nth(0).innerText())
        .toContain(descendingInstanceIds[0].toString());
      await expect
        .poll(() => instanceRows.nth(199).innerText())
        .toContain(descendingInstanceIds[199].toString());
      await page
        .getByRole('row', {name: `Instance ${descendingInstanceIds[199]}`})
        .scrollIntoViewIfNeeded();
      await expect(instanceRows).toHaveCount(200);
    });

    await test.step('Scroll to 250th instance', async () => {
      await expect
        .poll(() => instanceRows.nth(0).innerText())
        .toContain(descendingInstanceIds[50].toString());
      await expect
        .poll(() => instanceRows.nth(199).innerText())
        .toContain(descendingInstanceIds[249].toString());
    });
  });
});
