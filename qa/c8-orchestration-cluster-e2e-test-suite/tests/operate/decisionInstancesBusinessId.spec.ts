/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy} from 'utils/zeebeClient';
import {jsonHeaders} from 'utils/http';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {navigateToAppHome} from '@pages/UtilitiesPage';
import {
  OperateDecisionsPage,
  type AdvancedStringFilterOperator,
} from '@pages/OperateDecisionsPage';
import {waitForAssertion} from 'utils/waitForAssertion';
import {extendedAssertionOptions, uniqueBusinessId} from 'utils/constants';

const DMN_PROCESS_ID = 'mammalAnimalProcess';

const runPrefix = uniqueBusinessId('ui-dec-bizid');
const BUSINESS_ID_A = `${runPrefix}-aaa`;
const BUSINESS_ID_B = `${runPrefix}-zzz`;

async function startDmnProcessWithBusinessId(
  request: import('@playwright/test').APIRequestContext,
  businessId: string,
): Promise<void> {
  const res = await request.post('/v2/process-instances', {
    headers: jsonHeaders(),
    data: {
      processDefinitionId: DMN_PROCESS_ID,
      businessId,
      variables: {hasHairOrFur: true, warmBlooded: true, givesMilk: true},
    },
  });
  expect(res.status()).toBe(200);
  const json = await res.json();
  expect(json.processInstanceKey).toBeTruthy();
}

async function applyBusinessIdFilter(
  operateDecisionsPage: OperateDecisionsPage,
  value: string,
  operator?: AdvancedStringFilterOperator,
) {
  await operateDecisionsPage.displayOptionalFilter('Business ID');
  if (operator) {
    await operateDecisionsPage.selectBusinessIdFilterType(operator);
  }
  await operateDecisionsPage.fillBusinessIdFilter(value);
}

test.beforeAll(async ({request}) => {
  await deploy([
    './resources/mammalAnimalProcess.bpmn',
    './resources/isMammal_.dmn',
  ]);

  await startDmnProcessWithBusinessId(request, BUSINESS_ID_A);
  await startDmnProcessWithBusinessId(request, BUSINESS_ID_B);

  await test.step('Wait for both decision instances to be indexed', async () => {
    await expect
      .poll(
        async () => {
          const response = await request.post('/v2/decision-instances/search', {
            headers: jsonHeaders(),
            data: {filter: {businessId: {$like: `${runPrefix}-*`}}},
          });
          if (response.status() !== 200) return 0;
          const result = await response.json();
          return result.page?.totalItems ?? 0;
        },
        {timeout: 60_000, intervals: [2_000, 5_000]},
      )
      .toBeGreaterThanOrEqual(2);
  });
});

test.describe('Decision Instances - Business ID', () => {
  test.beforeEach(async ({page, operateHomePage}) => {
    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickDecisionsTab();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Filter decision instances by Business ID (equals)', async ({
    page,
    operateDecisionsPage,
  }) => {
    await test.step('Apply the Business ID equals filter', async () => {
      await applyBusinessIdFilter(operateDecisionsPage, BUSINESS_ID_A);
    });

    await test.step('Verify only the matching decision instance is shown', async () => {
      await waitForAssertion({
        assertion: async () => {
          const rowA = operateDecisionsPage.decisionInstancesList
            .getByRole('row')
            .filter({hasText: BUSINESS_ID_A});
          await expect(rowA).toBeVisible();
          await expect(rowA.getByTestId('cell-businessId')).toHaveText(
            BUSINESS_ID_A,
          );
          await expect(
            operateDecisionsPage.decisionInstancesList
              .getByRole('row')
              .filter({hasText: BUSINESS_ID_B}),
          ).toHaveCount(0);
        },
        onFailure: async () => {
          await page.reload();
          await applyBusinessIdFilter(operateDecisionsPage, BUSINESS_ID_A);
        },
      });
    });
  });

  test('Filter decision instances by Business ID (contains)', async ({
    page,
    operateDecisionsPage,
  }) => {
    await test.step('Apply the Business ID contains filter', async () => {
      await applyBusinessIdFilter(operateDecisionsPage, runPrefix, 'contains');
    });

    await test.step('Verify both decision instances sharing the prefix are shown', async () => {
      await waitForAssertion({
        assertion: async () => {
          await expect(
            operateDecisionsPage.decisionInstancesList
              .getByRole('row')
              .filter({hasText: BUSINESS_ID_A}),
          ).toBeVisible();
          await expect(
            operateDecisionsPage.decisionInstancesList
              .getByRole('row')
              .filter({hasText: BUSINESS_ID_B}),
          ).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await applyBusinessIdFilter(
            operateDecisionsPage,
            runPrefix,
            'contains',
          );
        },
      });
    });
  });

  test('Filter decision instances by a non-matching Business ID shows no results', async ({
    operateDecisionsPage,
  }) => {
    await test.step('Apply a Business ID filter that matches nothing', async () => {
      await applyBusinessIdFilter(operateDecisionsPage, `${runPrefix}-none`);
    });

    await test.step('Verify no decision instances are shown', async () => {
      await expect(
        operateDecisionsPage.decisionInstancesList
          .getByRole('row')
          .filter({hasText: runPrefix}),
      ).toHaveCount(0, {timeout: extendedAssertionOptions.timeout});
    });
  });

  test('Decision instance detail shows the Business ID', async ({
    page,
    operateDecisionsPage,
    operateDecisionInstancePage,
  }) => {
    const rowA = operateDecisionsPage.decisionInstancesList
      .getByRole('row')
      .filter({hasText: BUSINESS_ID_A});

    await test.step('Filter to the target decision instance by Business ID', async () => {
      await applyBusinessIdFilter(operateDecisionsPage, BUSINESS_ID_A);

      await waitForAssertion({
        assertion: async () => {
          await expect(rowA).toBeVisible();
        },
        onFailure: async () => {
          await page.reload();
          await applyBusinessIdFilter(operateDecisionsPage, BUSINESS_ID_A);
        },
      });
    });

    await test.step('Open the matching decision instance detail', async () => {
      await rowA.getByRole('link', {name: /View decision instance/}).click();
    });

    await test.step('Verify the detail header shows the Business ID', async () => {
      await expect(operateDecisionInstancePage.instanceHeader).toBeVisible();
      await expect(
        operateDecisionInstancePage.instanceHeader.getByText(BUSINESS_ID_A),
      ).toBeVisible();
    });
  });
});
