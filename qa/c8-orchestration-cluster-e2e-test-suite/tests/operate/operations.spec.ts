/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {randomUUID} from 'crypto';
import {
  deployWithSubstitutions,
  createInstances,
  createSingleInstance,
} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToApp} from '@pages/UtilitiesPage';
import {waitForAssertion} from 'utils/waitForAssertion';
import {sleep} from 'utils/sleep';

type ProcessInstance = {
  processInstanceKey: string;
  bpmnProcessId: string;
};

let initialData: {
  singleOperationInstance: ProcessInstance;
  batchOperationInstances: ProcessInstance[];
};

const runSuffix = randomUUID().slice(0, 8);
const processAId = `operationsProcessA-${runSuffix}`;
const processBId = `operationsProcessB-${runSuffix}`;

test.beforeAll(async () => {
  await deployWithSubstitutions('./resources/operationsProcessA.bpmn', {
    operationsProcessA: processAId,
  });
  await deployWithSubstitutions('./resources/operationsProcessB.bpmn', {
    operationsProcessB: processBId,
  });

  // operationsProcessA has a FEEL assertion (=assert(orderId, orderId!=null)) in its
  // service task io mapping. Creating the instance without 'orderId' immediately raises an
  // INPUT_OUTPUT_MAPPING_ERROR incident, which makes the Retry Instance button appear.
  const singleInstance = await createSingleInstance(processAId, 1);
  const batchInstances = await createInstances(processBId, 1, 10);

  initialData = {
    singleOperationInstance: {
      processInstanceKey: singleInstance.processInstanceKey,
      bpmnProcessId: processAId,
    },
    batchOperationInstances: batchInstances.map((instance) => ({
      processInstanceKey: instance.processInstanceKey,
      bpmnProcessId: processBId,
    })),
  };
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

  test('Retry and cancel single instance', async ({
    page,
    operateProcessesPage,
    operateFiltersPanelPage,
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
      await expect(operateProcessesPage.singleOperationSpinner).toBeHidden({
        timeout: 60000,
      });
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

    await test.step('Validate canceled instance details', async () => {
      const instanceRow = operateProcessesPage.getInstanceRow(0);

      await operateFiltersPanelPage.clickCanceledInstancesCheckbox();

      await expect(
        operateProcessesPage.getCanceledIcon(instance.processInstanceKey),
      ).toBeVisible();

      await expect(instanceRow.getByText(instance.bpmnProcessId)).toBeVisible();
      await expect(
        instanceRow.getByText(instance.processInstanceKey),
      ).toBeVisible();
    });
  });

  test('Retry and cancel multiple instances', async ({
    operateProcessesPage,
    operateFiltersPanelPage,
    page,
  }) => {
    test.slow();
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

      await operateProcessesPage.applyButton.click();
      await sleep(1000);

      await expect(
        operateProcessesPage.batchOperationStartedMessage('Resolve Incident'),
      ).toBeVisible({timeout: 60000});
    });

    await test.step('Cancel all instances', async () => {
      await operateProcessesPage.selectAllRowsCheckbox.click();

      await operateProcessesPage.cancelButton.click();
      await operateProcessesPage.applyButton.click();

      await operateFiltersPanelPage.clickIncidentsInstancesCheckbox();
      await operateFiltersPanelPage.clickCanceledInstancesCheckbox();

      await expect(
        operateProcessesPage.batchOperationStartedMessage(
          'Cancel Process Instance',
        ),
      ).toBeVisible({timeout: 60000});

      await waitForAssertion({
        assertion: async () => {
          await Promise.all(
            instances.map((instance) =>
              expect(
                operateProcessesPage.getCanceledIcon(
                  instance.processInstanceKey,
                ),
              ).toBeVisible({timeout: 5000}),
            ),
          );
        },
        onFailure: async () => {
          await page.reload();
        },
      });
    });
  });
});

let deleteTestData: {
  instances: ProcessInstance[];
};

const batchDeleteProcessId = `batchDeleteProcess-${runSuffix}`;

test.beforeAll(async () => {
  await deployWithSubstitutions('./resources/batch_delete_process.bpmn', {
    batchDeleteProcess: batchDeleteProcessId,
  });

  const instances = await createInstances(batchDeleteProcessId, 1, 5);
  deleteTestData = {
    instances: instances.map((instance) => ({
      processInstanceKey: instance.processInstanceKey,
      bpmnProcessId: batchDeleteProcessId,
    })),
  };

  // Allow time for all instances to auto-complete
  await sleep(5000);
});

test.describe('Delete Operations', () => {
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

  test('Delete completed instances in batch', async ({
    operateProcessesPage,
    operateFiltersPanelPage,
    page,
  }) => {
    test.slow();
    const instances = deleteTestData.instances;

    await test.step('Enable finished instances filter and filter by process instance keys', async () => {
      await operateFiltersPanelPage.clickFinishedInstancesCheckbox();
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

    await test.step('Select all completed instances', async () => {
      await operateProcessesPage.selectAllRowsCheckbox.click();
    });

    await test.step('Click Delete batch operation button', async () => {
      await expect(operateProcessesPage.deleteButton).toBeEnabled();
      await operateProcessesPage.deleteButton.click();
    });

    await test.step('Confirm deletion in modal', async () => {
      await operateProcessesPage.deleteBatchOperationConfirmButton.click();
    });

    await test.step('Verify batch delete operation started', async () => {
      await expect(
        operateProcessesPage.batchOperationStartedMessage(
          'Delete Process Instance',
        ),
      ).toBeVisible({timeout: 60000});
    });

    await test.step('Verify instances are removed from the list after deletion', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateProcessesPage.noMatchingInstancesMessage,
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
        },
        maxRetries: 5,
      });
    });
  });
});
