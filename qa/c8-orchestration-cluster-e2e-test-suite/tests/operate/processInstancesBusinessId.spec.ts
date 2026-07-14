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
import {deploy, cancelProcessInstance} from 'utils/zeebeClient';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToAppHome} from '@pages/UtilitiesPage';
import {OperateProcessesPage} from '@pages/OperateProcessesPage';
import {
  OperateFiltersPanelPage,
  type AdvancedStringFilterOperator,
} from '@pages/OperateFiltersPanelPage';
import {waitForAssertion} from 'utils/waitForAssertion';
import {createProcessInstanceWithBusinessId} from 'utils/requestHelpers/process-instance-requestHelpers';
import {extendedAssertionOptions} from 'utils/constants';

const USER_TASK_PROCESS_ID = 'user_task_api_test_process';

const runPrefix = `ui-bizid-${randomUUID().slice(0, 8)}`;
const BUSINESS_ID_A = `${runPrefix}-aaa`;
const BUSINESS_ID_B = `${runPrefix}-zzz`;

let instanceKeyA: string;
let instanceKeyB: string;

async function applyBusinessIdFilter(
  operateFiltersPanelPage: OperateFiltersPanelPage,
  value: string,
  operator?: AdvancedStringFilterOperator,
) {
  await operateFiltersPanelPage.displayOptionalFilter('Business ID');
  if (operator) {
    await operateFiltersPanelPage.selectBusinessIdFilterType(operator);
  }
  await operateFiltersPanelPage.fillBusinessIdFilter(value);
}

test.beforeAll(async ({request}) => {
  await deploy(['./resources/user_task_api_test_process.bpmn']);
  instanceKeyA = await createProcessInstanceWithBusinessId(
    request,
    USER_TASK_PROCESS_ID,
    BUSINESS_ID_A,
  );
  instanceKeyB = await createProcessInstanceWithBusinessId(
    request,
    USER_TASK_PROCESS_ID,
    BUSINESS_ID_B,
  );
});

test.afterAll(async () => {
  await Promise.allSettled(
    [instanceKeyA, instanceKeyB].map(async (key) => {
      if (!key) return;
      try {
        await cancelProcessInstance(key);
      } catch (error) {
        console.error(`Failed to cancel process instance ${key}:`, error);
      }
    }),
  );
});

test.describe('Process Instances - Business ID', () => {
  test.beforeEach(async ({page, operateHomePage}) => {
    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickProcessesTab();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Filter process instances by Business ID (equals)', async ({
    page,
    operateFiltersPanelPage,
  }) => {
    await test.step('Apply the Business ID equals filter', async () => {
      await applyBusinessIdFilter(operateFiltersPanelPage, BUSINESS_ID_A);
    });

    await test.step('Verify only the matching instance is shown', async () => {
      await waitForAssertion({
        assertion: async () => {
          const rowA = OperateProcessesPage.getRowByProcessInstanceKey(
            page,
            instanceKeyA,
          );
          await expect(rowA).toBeVisible();
          await expect(rowA.getByTestId('cell-businessId')).toHaveText(
            BUSINESS_ID_A,
          );
          await expect(
            OperateProcessesPage.getRowByProcessInstanceKey(page, instanceKeyB),
          ).toHaveCount(0);
        },
        onFailure: async () => {
          await page.reload();
          await applyBusinessIdFilter(operateFiltersPanelPage, BUSINESS_ID_A);
        },
      });
    });
  });

  test('Filter process instances by Business ID (contains)', async ({
    page,
    operateFiltersPanelPage,
  }) => {
    await test.step('Apply the Business ID contains filter', async () => {
      await applyBusinessIdFilter(
        operateFiltersPanelPage,
        runPrefix,
        'contains',
      );
    });

    await test.step('Verify both instances sharing the prefix are shown', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(
            OperateProcessesPage.getRowByProcessInstanceKey(page, instanceKeyA),
          ).toBeVisible();
          await expect(
            OperateProcessesPage.getRowByProcessInstanceKey(page, instanceKeyB),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await applyBusinessIdFilter(
            operateFiltersPanelPage,
            runPrefix,
            'contains',
          );
        },
      });
    });
  });

  test('Filter process instances by a non-matching Business ID shows no results', async ({
    operateProcessesPage,
    operateFiltersPanelPage,
  }) => {
    await test.step('Apply a Business ID filter that matches nothing', async () => {
      await applyBusinessIdFilter(operateFiltersPanelPage, `${runPrefix}-none`);
    });

    await test.step('Verify the empty-state message is shown', async () => {
      await expect(operateProcessesPage.noMatchingInstancesMessage).toBeVisible(
        {
          timeout: extendedAssertionOptions.timeout,
        },
      );
    });
  });

  test('Process instance detail shows the Business ID', async ({
    page,
    operateProcessesPage,
    operateFiltersPanelPage,
    operateProcessInstancePage,
  }) => {
    await test.step('Filter to the target instance by Business ID', async () => {
      await applyBusinessIdFilter(operateFiltersPanelPage, BUSINESS_ID_A);

      await waitForAssertion({
        assertion: async () => {
          await expect(
            OperateProcessesPage.getRowByProcessInstanceKey(page, instanceKeyA),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await applyBusinessIdFilter(operateFiltersPanelPage, BUSINESS_ID_A);
        },
      });
    });

    await test.step('Open the matching instance detail', async () => {
      await operateProcessesPage.processInstanceLinkByKey(instanceKeyA).click();
    });

    await test.step('Verify the detail header shows the Business ID', async () => {
      await expect(operateProcessInstancePage.instanceHeader).toBeVisible();
      await expect(
        operateProcessInstancePage.instanceHeader.getByText(BUSINESS_ID_A),
      ).toBeVisible();
    });
  });
});
