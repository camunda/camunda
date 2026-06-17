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
import {navigateToAppHome} from '@pages/UtilitiesPage';
import {jsonHeaders} from 'utils/http';

// Exposed at module level so tests can scope assertions to instances created by this spec.
let failedInstanceProcessKey: string;

test.beforeAll(async ({request}) => {
  await deploy([
    './resources/invoiceBusinessDecisions.dmn',
    './resources/invoice.bpmn',
  ]);

  await test.step('Create failed decision instance', async () => {
    const failedInstance = await createSingleInstance('invoice', 1);
    failedInstanceProcessKey = failedInstance.processInstanceKey;
  });

  await test.step('Create evaluated decision instance', async () => {
    await createSingleInstance('invoice', 1, {
      amount: 500,
      invoiceCategory: 'Misc',
    });
  });

  await test.step('Wait for failed instance to be indexed', async () => {
    await expect
      .poll(
        async () => {
          const response = await request.post('/v2/decision-instances/search', {
            headers: jsonHeaders(),
            data: {
              filter: {
                state: 'FAILED',
                processInstanceKey: failedInstanceProcessKey,
              },
            },
          });
          if (response.status() !== 200) return 0;
          const result = await response.json();
          return result.page?.totalItems ?? 0;
        },
        {timeout: 60_000, intervals: [2_000, 5_000]},
      )
      .toBeGreaterThanOrEqual(1);
  });
});

test.describe('Decision Filters', () => {
  test.beforeEach(async ({page, operateHomePage}) => {
    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickDecisionsTab();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Filter by decision name shows matching instances', async ({
    operateDecisionsPage,
  }) => {
    await test.step('Select decision name filter', async () => {
      await operateDecisionsPage.selectDecisionName('Invoice Classification');
    });

    await test.step('Verify filtered instances are visible', async () => {
      await expect(operateDecisionsPage.decisionInstancesList).toBeVisible();
      await expect(
        operateDecisionsPage.decisionInstancesList.getByRole('row'),
      ).not.toHaveCount(0);
    });

    await test.step('Verify name filter is visibly applied', async () => {
      await expect(operateDecisionsPage.decisionNameFilter).toHaveValue(
        'Invoice Classification',
      );
    });
  });

  test('Evaluated filter shows only evaluated instances', async ({
    operateDecisionsPage,
  }) => {
    let totalCount: number;

    await test.step('Filter by decision name to scope to test instances', async () => {
      await operateDecisionsPage.selectDecisionName('Invoice Classification');
    });

    await test.step('Get total row count with both filters active', async () => {
      await expect(operateDecisionsPage.decisionInstancesList).toBeVisible();
      // Wait for the failed instance to appear before reading the count.
      // This ensures both evaluated and failed instances are present so totalCount
      // reflects the full result set and the subsequent comparison is reliable.
      await expect(
        operateDecisionsPage.decisionInstancesList.getByRole('link', {
          name: `View process instance ${failedInstanceProcessKey}`,
        }),
      ).toBeVisible({timeout: 30_000});
      totalCount = await operateDecisionsPage.decisionInstancesList
        .getByRole('row')
        .count();
    });

    await test.step('Uncheck Failed filter', async () => {
      await operateDecisionsPage.clickFailedCheckbox();
      await expect(operateDecisionsPage.failedCheckbox).not.toBeChecked();
    });

    await test.step('Verify row count decreased (only evaluated shown)', async () => {
      await expect(
        operateDecisionsPage.decisionInstancesList.getByRole('row'),
      ).not.toHaveCount(totalCount);
      await expect(
        operateDecisionsPage.decisionInstancesList.getByRole('row'),
      ).not.toHaveCount(0);
      const evaluatedCount = await operateDecisionsPage.decisionInstancesList
        .getByRole('row')
        .count();
      expect(evaluatedCount).toBeLessThan(totalCount);
    });

    await test.step('Verify no failed instances appear in results', async () => {
      // The failed instance's process key link must be absent — the row only appears
      // when the Failed filter is active. Using the process-instance link is more
      // reliable than getByText('Evaluation failed'), which is icon-only in the UI.
      await expect(
        operateDecisionsPage.decisionInstancesList.getByRole('link', {
          name: `View process instance ${failedInstanceProcessKey}`,
        }),
      ).toHaveCount(0);
    });
  });

  test('Failed filter shows only failed instances', async ({
    operateDecisionsPage,
  }) => {
    let totalCount: number;

    await test.step('Filter by decision name to scope to test instances', async () => {
      await operateDecisionsPage.selectDecisionName('Invoice Classification');
    });

    await test.step('Get total row count with both filters active', async () => {
      await expect(operateDecisionsPage.decisionInstancesList).toBeVisible();
      // Wait until the failed instance's process-instance link is visible in the list.
      // This is more reliable than checking for 'Evaluation failed' text, which is
      // rendered as an icon (no DOM text) in the Operate UI.
      await expect(
        operateDecisionsPage.decisionInstancesList.getByRole('link', {
          name: `View process instance ${failedInstanceProcessKey}`,
        }),
      ).toBeVisible({timeout: 30_000});
      totalCount = await operateDecisionsPage.decisionInstancesList
        .getByRole('row')
        .count();
    });

    await test.step('Uncheck Evaluated filter', async () => {
      await operateDecisionsPage.clickEvaluatedCheckbox();
      await expect(operateDecisionsPage.evaluatedCheckbox).not.toBeChecked();
    });

    await test.step('Verify row count decreased (only failed shown)', async () => {
      await expect(
        operateDecisionsPage.decisionInstancesList.getByRole('row'),
      ).not.toHaveCount(totalCount);
      await expect(
        operateDecisionsPage.decisionInstancesList.getByRole('row'),
      ).not.toHaveCount(0);
      const failedCount = await operateDecisionsPage.decisionInstancesList
        .getByRole('row')
        .count();
      expect(failedCount).toBeLessThan(totalCount);
    });
  });
});

test.describe('Decision Filter Reset', () => {
  test.beforeEach(async ({page, operateHomePage}) => {
    await navigateToAppHome(page, 'operate');
    await expect(operateHomePage.operateBanner).toBeVisible();
    await operateHomePage.clickDecisionsTab();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Clicking Decisions header link resets filters to default', async ({
    operateDecisionsPage,
    operateHomePage,
  }) => {
    await test.step('Apply decision name filter', async () => {
      await operateDecisionsPage.selectDecisionName('Invoice Classification');
      await expect(operateDecisionsPage.decisionNameFilter).toHaveValue(
        'Invoice Classification',
      );
    });

    await test.step('Show an optional filter', async () => {
      await operateDecisionsPage.displayOptionalFilter(
        'Decision Instance Key(s)',
      );
      await expect(
        operateDecisionsPage.decisionInstanceKeysFilter,
      ).toBeVisible();
    });

    await test.step('Click the Decisions header link', async () => {
      await operateHomePage.decisionsTab.click();
    });

    await test.step('Verify decision name filter is cleared', async () => {
      await expect(operateDecisionsPage.decisionNameFilter).not.toHaveValue(
        'Invoice Classification',
      );
    });

    await test.step('Verify Evaluated and Failed checkboxes are both selected', async () => {
      await expect(operateDecisionsPage.evaluatedCheckbox).toBeChecked();
      await expect(operateDecisionsPage.failedCheckbox).toBeChecked();
    });

    await test.step('Verify no optional filters are visible', async () => {
      await expect(
        operateDecisionsPage.decisionInstanceKeysFilter,
      ).toBeHidden();
    });
  });
});
