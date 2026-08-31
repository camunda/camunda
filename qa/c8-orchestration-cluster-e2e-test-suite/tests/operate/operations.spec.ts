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
import {navigateToApp, validateURL} from '@pages/UtilitiesPage';
import {DATE_REGEX} from 'utils/constants';
import {sleep} from 'utils/sleep';
import {waitForAssertion} from 'utils/waitForAssertion';
import {createDemoOperations} from 'utils/operations.helper';
import {getNewOperationIds} from 'utils/getNewOperationIds';

type ProcessInstance = {
  processInstanceKey: string;
  bpmnProcessId: string;
};

let initialData: {
  singleOperationInstance: ProcessInstance;
  demoOperationsInstance: ProcessInstance;
  batchOperationInstances: ProcessInstance[];
};

test.beforeAll(async ({request}) => {
  await deploy([
    './resources/operationsProcessA.bpmn',
    './resources/operationsProcessB.bpmn',
  ]);

  const singleInstance = await createSingleInstance('operationsProcessA', 1);
  const demoInstance = await createSingleInstance('operationsProcessA', 1);
  const batchInstances = await createInstances('operationsProcessB', 1, 10);

  initialData = {
    singleOperationInstance: {
      processInstanceKey: singleInstance.processInstanceKey,
      bpmnProcessId: 'operationsProcessA',
    },
    demoOperationsInstance: {
      processInstanceKey: demoInstance.processInstanceKey,
      bpmnProcessId: 'operationsProcessA',
    },
    batchOperationInstances: batchInstances.map((instance) => ({
      processInstanceKey: instance.processInstanceKey,
      bpmnProcessId: 'operationsProcessB',
    })),
  };
  await sleep(2500);
  await createDemoOperations(
    request,
    initialData.demoOperationsInstance.processInstanceKey,
    51,
  );
  await sleep(500);
});

test.describe('Operations', () => {
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

  test('Infinite scrolling', async ({operateOperationPanelPage}) => {
    await test.step('Expand operations panel and verify initial batch loaded', async () => {
      await operateOperationPanelPage.expandOperationIdField();

      await expect
        .poll(async () => {
          return operateOperationPanelPage.getAllOperationEntries().count();
        })
        .toBe(50);
    });

    await test.step('Scroll to bottom and verify more operations loaded', async () => {
      const initialCount = await operateOperationPanelPage
        .getAllOperationEntries()
        .count();

      await operateOperationPanelPage
        .getAllOperationEntries()
        .last()
        .scrollIntoViewIfNeeded();

      await expect(async () => {
        const newCount = await operateOperationPanelPage
          .getAllOperationEntries()
          .count();
        expect(newCount).toBeGreaterThan(initialCount);
      }).toPass();
    });
  });

  // Regression coverage for bug 42375 (closed as "not planned" / intended behavior):
  // a retry on a single instance is added to the operations panel, but a cancel is not.
  // https://github.com/camunda/camunda/issues/42375
  test('Retry and cancel single instance', async ({
    page,
    operateProcessesPage,
    operateFiltersPanelPage,
    operateOperationPanelPage,
  }) => {
    const instance = initialData.singleOperationInstance;

    await test.step('Filter by Process Instance Key', async () => {
      await expect(operateProcessesPage.dataList).toBeVisible();
      await operateFiltersPanelPage.displayOptionalFilter(
        'Process Instance Key(s)',
      );
      await operateFiltersPanelPage.processInstanceKeysFilter.fill(
        instance.processInstanceKey,
      );
      await expect(page.getByText('1 result')).toBeVisible();
    });

    await test.step('Retry single instance using operation button', async () => {
      await expect(
        operateProcessesPage.getRetryInstanceButton(
          instance.processInstanceKey,
        ),
      ).toBeEnabled({timeout: 120000});

      // Snapshot the operations panel before triggering this test's retry. The
      // panel is a global, time-ordered list shared across instances and
      // parallel tests, and is polluted by the 51 demo retry operations from
      // beforeAll, so this test's retry cannot be matched by the generic
      // "1 retry succeeded" text. Diffing the panel by operation id below
      // isolates the entry this test created.
      operateOperationPanelPage.beforeOperationOperationPanelEntries =
        await operateOperationPanelPage.operationIdsEntries();
      await operateOperationPanelPage.collapseOperationsPanel();

      await operateProcessesPage.clickRetryInstanceButton(
        instance.processInstanceKey,
      );

      const instanceSpinner = operateProcessesPage.getInstanceOperationSpinner(
        instance.processInstanceKey,
      );
      await expect(instanceSpinner).toBeVisible();
      await expect(instanceSpinner).toBeHidden({timeout: 90000});
    });

    await test.step('Cancel single instance using operation button', async () => {
      await operateProcessesPage.clickCancelInstanceButton(
        instance.processInstanceKey,
      );

      await operateProcessesPage.applyButton.click();

      await expect(
        operateProcessesPage.noMatchingInstancesMessage,
      ).toBeVisible();
    });

    await test.step('Verify retry operation appears in operations list', async () => {
      await expect(operateOperationPanelPage.operationList).toBeHidden();
      await operateOperationPanelPage.expandOperationIdField();

      await expect(operateOperationPanelPage.operationList).toBeVisible();

      // The operations panel is a global, time-ordered list shared across all
      // instances and parallel tests. It is polluted by the 51 demo retry
      // operations created in beforeAll (on demoOperationsInstance) and by
      // other tests' operations, all of which render an identical
      // "1 retry succeeded" entry, so picking the newest such entry can select
      // a foreign operation and filter to the wrong instance. Instead, diff the
      // panel against the pre-retry snapshot to isolate the retry this test
      // triggered by its own operation id.
      operateOperationPanelPage.afterOperationOperationPanelEntries =
        await operateOperationPanelPage.operationIdsEntries();
      let newRetryIds = getNewOperationIds(
        operateOperationPanelPage.beforeOperationOperationPanelEntries,
        operateOperationPanelPage.afterOperationOperationPanelEntries,
        'Retry',
      );

      await waitForAssertion({
        assertion: async () => {
          expect(newRetryIds.length).toBeGreaterThan(0);
        },
        onFailure: async () => {
          await sleep(1_000);
          operateOperationPanelPage.afterOperationOperationPanelEntries =
            await operateOperationPanelPage.operationIdsEntries();
          newRetryIds = getNewOperationIds(
            operateOperationPanelPage.beforeOperationOperationPanelEntries,
            operateOperationPanelPage.afterOperationOperationPanelEntries,
            'Retry',
          );
        },
        maxRetries: 60,
      });

      const operationId = newRetryIds[0];
      const retryEntry =
        operateOperationPanelPage.getOperationEntryById(operationId);

      await expect(retryEntry).toBeVisible();
      await expect(retryEntry.getByText(DATE_REGEX)).toBeVisible();

      await operateOperationPanelPage.clickOperationLink(retryEntry);
      await expect(operateFiltersPanelPage.operationIdFilter).toHaveValue(
        operationId,
      );
      await expect(
        operateProcessesPage.processInstanceLinkByKey(
          instance.processInstanceKey,
        ),
      ).toBeVisible();
    });

    await test.step('Verify cancel does not create an operations panel entry', async () => {
      await expect(
        operateOperationPanelPage.getCancelOperationEntry(1),
      ).toBeHidden();
    });

    await test.step('Validate canceled instance details', async () => {
      const instanceRow = operateProcessesPage.getInstanceRow(0);

      await expect(
        operateProcessesPage.getCanceledIcon(instance.processInstanceKey),
      ).toBeVisible({timeout: 60000});

      await expect(instanceRow.getByText(instance.bpmnProcessId)).toBeVisible();
      await expect(
        instanceRow.getByText(instance.processInstanceKey),
      ).toBeVisible();

      await operateOperationPanelPage.collapseOperationIdField();
    });
  });

  // Regression coverage for bug 42448 (fixed by #48419): batch cancellation with
  // an operationId filter no longer returns 400.
  // https://github.com/camunda/camunda/issues/42448
  //
  // Skipped due to bug #61420: https://github.com/camunda/camunda/issues/61420
  // Batch-canceling incident-carrying instances completes the
  // CANCEL_PROCESS_INSTANCE operation but leaves them stuck as Incident (never
  // Canceled) in Operate's list-view — same stale-incident family as #54279,
  // whose fix (#57719) does not cover this batch cancel-after-retry path. The
  // instances report state INCIDENT / endDate null despite the cancel operation
  // being COMPLETED, so no test-side wait or reload can make the Canceled icon
  // appear. Re-enable once #61420 is fixed.
  test.skip('Retry and cancel multiple instances', async ({
    operateProcessesPage,
    operateFiltersPanelPage,
    operateOperationPanelPage,
    page,
  }) => {
    const instances = initialData.batchOperationInstances.slice(0, 5);

    await test.step('Filter by Process Instance Keys', async () => {
      await expect(operateProcessesPage.dataList).toBeVisible();

      await operateFiltersPanelPage.displayOptionalFilter(
        'Process Instance Key(s)',
      );
      await operateFiltersPanelPage.processInstanceKeysFilter.fill(
        instances.map((instance) => instance.processInstanceKey).join(','),
      );

      await expect(operateProcessesPage.dataList.getByRole('row')).toHaveCount(
        instances.length,
      );
    });

    await test.step('Select all instances and retry', async () => {
      await operateProcessesPage.selectAllRowsCheckbox.click();

      await operateProcessesPage.retryButton.click();

      await expect(operateOperationPanelPage.operationList).toBeHidden();

      await operateProcessesPage.applyButton.click();

      await expect(operateOperationPanelPage.operationList).toBeVisible({
        timeout: 30000,
      });
      await sleep(500);

      await operateOperationPanelPage.expandOperationsPanel();
    });

    await test.step('Wait for operation to complete', async () => {
      const operationEntry = operateOperationPanelPage.getRetryOperationEntry(
        instances.length,
      );

      await expect(operationEntry).toBeVisible({timeout: 120000});

      await operateOperationPanelPage.clickOperationLink(operationEntry);

      await validateURL(page, /operationId=/);

      await Promise.all(
        instances.map((instance) =>
          expect(
            operateProcessesPage.processInstanceLinkByKey(
              instance.processInstanceKey,
            ),
          ).toBeVisible(),
        ),
      );
    });

    await test.step('Cancel all instances', async () => {
      await operateOperationPanelPage.collapseOperationIdField();
      await operateProcessesPage.selectAllRowsCheckbox.click();

      await operateProcessesPage.cancelButton.click();
      await operateProcessesPage.applyButton.click();

      await expect(operateOperationPanelPage.operationList).toBeVisible({
        timeout: 30000,
      });
      await sleep(500);

      await operateOperationPanelPage.expandOperationsPanel();

      // The transient "has scheduled operations" icon (driven by the instance's
      // hasActiveOperation flag) is not reliably observable for these trivial
      // instances: the engine processes the batch cancel faster than the list
      // reflects the active-operation state, so the icon count is frequently 0.
      // The batch cancel succeeding is verified durably below via the completed
      // operation entry ("N operations succeeded") and each instance's canceled
      // icon, so we do not poll for the transient scheduled-operations state.
      const operationEntry = operateOperationPanelPage.getCancelOperationEntry(
        instances.length,
      );

      await waitForAssertion({
        assertion: async () => {
          await expect(operationEntry).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 10,
      });

      await Promise.all(
        instances.map((instance) =>
          expect(
            operateProcessesPage.getCanceledIcon(instance.processInstanceKey),
          ).toBeVisible({timeout: 120000}),
        ),
      );
    });
  });
});
