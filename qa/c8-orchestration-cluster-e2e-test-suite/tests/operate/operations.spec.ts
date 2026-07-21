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
  await sleep(2500);
  await createDemoOperations(
    request,
    initialData.singleOperationInstance.processInstanceKey,
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
      ).toBeVisible({timeout: 120000});
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

      const retryEntry = operateOperationPanelPage
        .getAllOperationEntries()
        .filter({hasText: 'Retry'})
        .first();

      await expect(retryEntry).toBeVisible();
      await expect(retryEntry.getByText(DATE_REGEX)).toBeVisible();

      const operationId = await retryEntry.getByRole('link').innerText();

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

  // Skipped due to bug 42448: https://github.com/camunda/camunda/issues/42448
  // !Note: assert the code after the bug is fixed as it was dicoverd during the test implementation
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

      await expect
        .poll(async () => {
          return operateProcessesPage.scheduledOperationsIcons.count();
        })
        .toBe(instances.length);

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
