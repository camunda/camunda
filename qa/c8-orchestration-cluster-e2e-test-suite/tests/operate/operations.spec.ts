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
    50,
  );
  await sleep(1000);
  await waitForIncidents(
    request,
    initialData.singleOperationInstance.processInstanceKey,
  );
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
        .toBe(20);
    });

    await test.step('Scroll to bottom and verify more operations loaded', async () => {
      await operateOperationPanelPage
        .getAllOperationEntries()
        .nth(19)
        .scrollIntoViewIfNeeded();

      await expect(
        operateOperationPanelPage.getAllOperationEntries(),
      ).toHaveCount(40, {timeout: 30000});
    });
  });

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

      const operationEntry =
        operateOperationPanelPage.getCancelOperationEntry(1);

      await waitForAssertion({
        assertion: async () => {
          await expect(operationEntry).toBeVisible();
          await expect(operationEntry.getByText(DATE_REGEX)).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 10,
      });

      const operationId = await operationEntry.getByRole('link').innerText();

      await operateOperationPanelPage.clickOperationLink(operationEntry);
      await expect(operateFiltersPanelPage.operationIdFilter).toHaveValue(
        operationId,
      );
      await expect(page.getByText('1 result')).toBeVisible();
    });

    await test.step('Validate canceled instance details', async () => {
      const instanceRow = operateProcessesPage.getInstanceRow(0);

      await expect(
        operateProcessesPage.getCanceledIcon(instance.processInstanceKey),
      ).toBeVisible();

      await expect(instanceRow.getByText(instance.bpmnProcessId)).toBeVisible();
      await expect(
        instanceRow.getByText(instance.processInstanceKey),
      ).toBeVisible();

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

      await waitForAssertion({
        assertion: async () => {
          await expect(operationEntry).toBeVisible({timeout: 30000});
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 10,
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
