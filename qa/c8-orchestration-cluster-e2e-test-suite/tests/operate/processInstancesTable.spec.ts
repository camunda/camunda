/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect, Locator, Page} from '@playwright/test';
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

async function scrollUntilElementIsVisible(page: Page, locator: Locator) {
  while (!(await locator.isVisible())) {
    await page.mouse.wheel(0, 600);
  }
}

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

  // Skipped due to bug 38103: https://github.com/camunda/camunda/issues/38103
  test.skip('Sorting of process instances', async ({
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
        .toContain(instanceIds[1].toString());
      await expect
        .poll(() =>
          operateProcessesPage.processInstancesTable.nth(2).innerText(),
        )
        .toContain(instanceIds[0].toString());
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
      await scrollUntilElementIsVisible(
        page,
        page.getByRole('row', {name: `Instance ${descendingInstanceIds[50]}`}),
      );

      await expect(instanceRows).toHaveCount(100);
    });

    await test.step('Scroll to 150th instance', async () => {
      await scrollUntilElementIsVisible(
        page,
        page.getByRole('row', {name: `Instance ${descendingInstanceIds[100]}`}),
      );
      await expect(instanceRows).toHaveCount(150);
    });

    await test.step('Scroll to 200th instance', async () => {
      await scrollUntilElementIsVisible(
        page,
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
      await scrollUntilElementIsVisible(
        page,
        page.getByRole('row', {name: `Instance ${descendingInstanceIds[200]}`}),
      );
      await expect(instanceRows).toHaveCount(200);
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
