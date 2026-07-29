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
import {waitForIncidents} from 'utils/incidentsHelper';
import {getNewOperationIds} from 'utils/getNewOperationIds';

type ProcessInstance = {
  processInstanceKey: string;
  bpmnProcessId: string;
};

let initialData: {
  singleOperationInstance: ProcessInstance;
  batchOperationInstances: ProcessInstance[];
};

test.beforeAll(async ({request}) => {
  await deploy([
    './resources/operationsProcessA.bpmn',
    './resources/operationsProcessB.bpmn',
  ]);

  const singleInstance = await createSingleInstance('operationsProcessA', 1);
  const batchInstances = await createInstances('operationsProcessB', 1, 10);

  initialData = {
    singleOperationInstance: {
      processInstanceKey: singleInstance.processInstanceKey,
      bpmnProcessId: 'operationsProcessA',
    },
    batchOperationInstances: batchInstances.map((instance) => ({
      processInstanceKey: instance.processInstanceKey,
      bpmnProcessId: 'operationsProcessB',
    })),
  };
  await sleep(1000);
  await waitForIncidents(
    request,
    initialData.singleOperationInstance.processInstanceKey,
  );
});

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

test.describe('Operations', () => {
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
      await operateProcessesPage.clickRetryInstanceButton(
        instance.processInstanceKey,
      );

      await expect(operateProcessesPage.singleOperationSpinner).toBeVisible();
      await expect
        .poll(() => operateProcessesPage.singleOperationSpinner.isVisible(), {
          timeout: 60000,
        })
        .toBeFalsy();
    });

    await test.step('Cancel single instance using operation button', async () => {
      // operations-list is cluster-wide (fed by /api/batch-operations), so other
      // concurrently-running specs' operations land in it too. Snapshot the IDs
      // present before this action so the operation we just triggered can be
      // identified by ID afterwards, instead of by ambient type+count text that
      // another spec's operation could equally match.
      operateOperationPanelPage.beforeOperationOperationPanelEntries =
        await operateOperationPanelPage.operationIdsEntries();

      await operateProcessesPage.clickCancelInstanceButton(
        instance.processInstanceKey,
      );

      await operateProcessesPage.applyButton.click();

      await expect(operateProcessesPage.noMatchingInstancesMessage).toBeVisible(
        {timeout: 60000},
      );
    });

    await test.step('Validate operation in operations list', async () => {
      await expect(operateOperationPanelPage.operationList).toBeHidden();
      await operateOperationPanelPage.expandOperationIdField();

      await expect(operateOperationPanelPage.operationList).toBeVisible();

      // The cancel operation's success count is only rendered once Operate's
      // importer marks the cancellation complete, which can lag under load.
      // Reload and retry so this importer delay does not flake — via the
      // captured URL, so the current filtered view is restored rather than
      // dropping a fill-based filter on a bare reload.
      const filteredProcessUrl = page.url();
      let cancelOperationIds: string[] = [];
      await waitForAssertion({
        assertion: async () => {
          operateOperationPanelPage.afterOperationOperationPanelEntries =
            await operateOperationPanelPage.operationIdsEntries();
          cancelOperationIds = getNewOperationIds(
            operateOperationPanelPage.beforeOperationOperationPanelEntries,
            operateOperationPanelPage.afterOperationOperationPanelEntries,
            'Cancel',
          );
          expect(cancelOperationIds.length).toBeGreaterThan(0);

          const operationEntry =
            operateOperationPanelPage.getOperationEntryById(
              cancelOperationIds[0],
            );
          await expect(operationEntry).toBeVisible();
          await expect(operationEntry.getByText(DATE_REGEX)).toBeVisible();
        },
        onFailure: async () => {
          await page.goto(filteredProcessUrl);
        },
        maxRetries: 15,
      });

      const operationId = cancelOperationIds[0];
      const operationEntry =
        operateOperationPanelPage.getOperationEntryById(operationId);

      await operateOperationPanelPage.clickOperationLink(operationEntry);
      await expect(operateFiltersPanelPage.operationIdFilter).toHaveValue(
        operationId,
      );
      await expect(page.getByText('1 result')).toBeVisible();
    });

    await test.step('Validate canceled instance details', async () => {
      // Restore this exact (operationId-filtered) view on retry instead of a
      // bare reload, keeping recovery consistent with the rest of the test.
      const canceledInstanceUrl = page.url();
      await waitForAssertion({
        assertion: async () => {
          const instanceRow = operateProcessesPage.getInstanceRow(0);

          await expect(
            operateProcessesPage.getCanceledIcon(instance.processInstanceKey),
          ).toBeVisible({timeout: 30000});

          await expect(
            instanceRow.getByText(instance.bpmnProcessId),
          ).toBeVisible();
          await expect(
            instanceRow.getByText(instance.processInstanceKey),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.goto(canceledInstanceUrl);
        },
        maxRetries: 8,
      });

      await operateOperationPanelPage.collapseOperationIdField();
    });
  });

  test('Retry and cancel multiple instances', async ({
    operateProcessesPage,
    operateFiltersPanelPage,
    operateOperationPanelPage,
    page,
  }) => {
    const instances = initialData.batchOperationInstances.slice(0, 5);
    // Operate encodes the instance-key filter in the URL query string. Capture
    // it once the filter is applied so recovery reloads can restore the exact
    // filtered view: a bare page.reload() drops the fill-based filter, which is
    // why reloads here have been unreliable.
    let filteredProcessesUrl = '';

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

      filteredProcessesUrl = page.url();
    });

    await test.step('Select all instances and retry', async () => {
      await operateProcessesPage.selectAllRowsCheckbox.click();

      // Snapshot before triggering, so the operation this test creates can be
      // told apart by ID from any operation another concurrently-running spec
      // adds to this same cluster-wide operations-list.
      operateOperationPanelPage.beforeOperationOperationPanelEntries =
        await operateOperationPanelPage.operationIdsEntries();

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
      let retryOperationIds: string[] = [];
      await waitForAssertion({
        assertion: async () => {
          operateOperationPanelPage.afterOperationOperationPanelEntries =
            await operateOperationPanelPage.operationIdsEntries();
          retryOperationIds = getNewOperationIds(
            operateOperationPanelPage.beforeOperationOperationPanelEntries,
            operateOperationPanelPage.afterOperationOperationPanelEntries,
            'Retry',
          );
          expect(retryOperationIds.length).toBeGreaterThan(0);
        },
        onFailure: async () => {
          await page.goto(filteredProcessesUrl);
        },
        maxRetries: 10,
      });

      const operationEntry = operateOperationPanelPage.getOperationEntryById(
        retryOperationIds[0],
      );
      // Wait for the full batch to succeed, not just for the entry to appear.
      // Reload via the captured (filter-preserving) URL to refresh a lagging
      // panel count instead of polling a possibly-stale panel for the full 60s.
      await waitForAssertion({
        assertion: async () => {
          await expect(
            operationEntry.getByText(`${instances.length} success`),
          ).toBeVisible({timeout: 20000});
        },
        onFailure: async () => {
          await page.goto(filteredProcessesUrl);
          await operateOperationPanelPage.expandOperationsPanel();
        },
        maxRetries: 3,
      });

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

      // Snapshot before triggering, so the operation this test creates can be
      // told apart by ID from any operation another concurrently-running spec
      // adds to this same cluster-wide operations-list.
      operateOperationPanelPage.beforeOperationOperationPanelEntries =
        await operateOperationPanelPage.operationIdsEntries();

      await operateProcessesPage.cancelButton.click();
      await operateProcessesPage.applyButton.click();

      await expect(operateOperationPanelPage.operationList).toBeVisible({
        timeout: 30000,
      });
      await sleep(500);

      await operateOperationPanelPage.expandOperationsPanel();

      await expect
        .poll(async () => {
          return operateProcessesPage.scheduledOperationsIcons.count();
        })
        .toBe(instances.length);

      let cancelOperationIds: string[] = [];
      await waitForAssertion({
        assertion: async () => {
          operateOperationPanelPage.afterOperationOperationPanelEntries =
            await operateOperationPanelPage.operationIdsEntries();
          cancelOperationIds = getNewOperationIds(
            operateOperationPanelPage.beforeOperationOperationPanelEntries,
            operateOperationPanelPage.afterOperationOperationPanelEntries,
            'Cancel',
          );
          expect(cancelOperationIds.length).toBeGreaterThan(0);
        },
        onFailure: async () => {
          await page.goto(filteredProcessesUrl);
        },
        maxRetries: 10,
      });

      const operationEntry = operateOperationPanelPage.getOperationEntryById(
        cancelOperationIds[0],
      );
      // Wait for the full batch to succeed, not just for the entry to appear.
      // Reload via the captured (filter-preserving) URL to refresh a lagging
      // panel count instead of polling a possibly-stale panel for the full 60s.
      await waitForAssertion({
        assertion: async () => {
          await expect(
            operationEntry.getByText(`${instances.length} success`),
          ).toBeVisible({timeout: 20000});
        },
        onFailure: async () => {
          await page.goto(filteredProcessesUrl);
          await operateOperationPanelPage.expandOperationsPanel();
        },
        maxRetries: 3,
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

test.describe('Operations panel scrolling', () => {
  // The 50 demo operations exist solely to populate the Operations panel for the
  // "Infinite scrolling" test. They are CANCEL_PROCESS_INSTANCE operations, one
  // per dedicated throwaway instance, so each completes and terminates its
  // instance rather than requeuing. This keeps Operate's shared
  // operation-executor from being saturated, which previously starved the
  // operation-success assertions in the retry/cancel tests here and in other
  // specs (e.g. process instance migration). The describe still runs after the
  // retry/cancel tests so the transient cancel burst cannot interfere with them.
  test.beforeAll(async () => {
    const demoInstances = await createInstances('operationsProcessA', 1, 50);
    await createDemoOperations(
      demoInstances.map((instance) => instance.processInstanceKey),
    );
  });

  test('Infinite scrolling', async ({operateOperationPanelPage}) => {
    await test.step('Expand operations panel and verify initial batch loaded', async () => {
      await operateOperationPanelPage.expandOperationIdField();

      await expect
        .poll(async () => {
          return operateOperationPanelPage.getAllOperationEntries().count();
        })
        .toBe(20);
    });

    await test.step('Scroll to bottom and verify more operations loaded', async () => {
      await operateOperationPanelPage
        .getAllOperationEntries()
        .nth(19)
        .scrollIntoViewIfNeeded();

      // Scrolling loads the next page (20 more) via cursor pagination, while the
      // operations store also polls the top `20 * page` operations every second
      // as long as any operation is still running. The two result sets are merged
      // by id, and because operations are still mutating (sort order drifts as the
      // demo cancels complete) the merged distinct set can slightly exceed a clean
      // multiple of the page size. Assert that at least a second batch loaded
      // rather than an exact count, which is inherently racy under that merge.
      await expect
        .poll(
          async () =>
            operateOperationPanelPage.getAllOperationEntries().count(),
          {timeout: 30000},
        )
        .toBeGreaterThanOrEqual(40);
    });
  });
});
