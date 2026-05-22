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
import {sleep} from 'utils/sleep';

test.beforeAll(async () => {
  await deploy([
    './resources/invoiceBusinessDecisions.dmn',
    './resources/invoice.bpmn',
  ]);

  // No variables → decision evaluation error → FAILED decision instance
  await createSingleInstance('invoice', 1);

  // Valid inputs → decision evaluates successfully → EVALUATED decision instance
  await createSingleInstance('invoice', 1, {
    amount: 500,
    invoiceCategory: 'Misc',
  });

  await sleep(2000);
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
    page,
  }) => {
    let totalCount: number;

    await test.step('Filter by decision name to scope to test instances', async () => {
      await operateDecisionsPage.selectDecisionName('Invoice Classification');
    });

    await test.step('Get total row count with both filters active', async () => {
      await expect(operateDecisionsPage.decisionInstancesList).toBeVisible();
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

    await test.step('Verify Failed checkbox is not visible in filter panel', async () => {
      await expect(page.getByText('Evaluation failed')).toHaveCount(0);
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
