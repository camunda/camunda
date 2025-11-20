/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '../visual-fixtures';
import {
  mockBatchOperations,
  mockDecisionInstances,
  mockDecisionXml,
  mockedDecisionDefinitions,
  mockEmptyDecisionInstances,
  mockResponses,
} from '../mocks/decisions.mocks';
import {URL_API_PATTERN} from '../constants';
import {clientConfigMock} from '../mocks/clientConfig';

test.beforeEach(async ({context}) => {
  await context.route('**/client-config.js', (route) =>
    route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/javascript;charset=UTF-8',
      },
      body: clientConfigMock,
    }),
  );
});

test.describe('decisions page', () => {
  test('empty page', async ({page, decisionsPage}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        'panelStates',
        JSON.stringify({
          isOperationsCollapsed: false,
        }),
      );
    });

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        batchOperations: {items: [], page: {totalItems: 0}},
        decisionDefinitions: mockedDecisionDefinitions,
        decisionXml: '',
        decisionInstances: mockEmptyDecisionInstances,
      }),
    );

    await decisionsPage.gotoDecisionsPage({
      searchParams: {
        evaluated: 'true',
        failed: 'true',
      },
    });

    await expect(page).toHaveScreenshot();
  });

  test('error page', async ({page, decisionsPage}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        'panelStates',
        JSON.stringify({
          isDecisionsFiltersCollapsed: true,
          isOperationsCollapsed: false,
        }),
      );
    });

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionDefinitions: mockedDecisionDefinitions,
      }),
    );

    await decisionsPage.gotoDecisionsPage({
      searchParams: {
        evaluated: 'true',
        failed: 'true',
        name: 'invoiceClassification',
        version: '2',
      },
    });

    await expect(decisionsPage.fetchDecisionErrorMessage).toBeVisible();
    await expect(decisionsPage.diagramSpinner).not.toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('filled with data', async ({page, decisionsPage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionDefinitions: mockedDecisionDefinitions,
        batchOperations: mockBatchOperations,
        decisionInstances: mockDecisionInstances,
        decisionXml: mockDecisionXml,
      }),
    );

    await decisionsPage.gotoDecisionsPage({
      searchParams: {
        evaluated: 'true',
        failed: 'true',
        name: 'invoiceClassification',
        version: '2',
      },
    });

    await expect(page).toHaveScreenshot();
  });

  test('filled with data and operations panel expanded', async ({
    page,
    decisionsPage,
  }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        'panelStates',
        JSON.stringify({
          isOperationsCollapsed: false,
        }),
      );
    });

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionDefinitions: mockedDecisionDefinitions,
        batchOperations: mockBatchOperations,
        decisionInstances: mockDecisionInstances,
        decisionXml: mockDecisionXml,
      }),
    );

    await decisionsPage.gotoDecisionsPage({
      searchParams: {
        evaluated: 'true',
        failed: 'true',
        name: 'invoiceClassification',
        version: '2',
      },
    });

    await expect(page).toHaveScreenshot();
  });

  test('optional filters visible', async ({page, decisionsPage}) => {
    await page.addInitScript(() => {
      window.localStorage.setItem(
        'panelStates',
        JSON.stringify({
          isOperationsCollapsed: false,
        }),
      );
    });

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionDefinitions: mockedDecisionDefinitions,
        batchOperations: mockBatchOperations,
        decisionInstances: mockDecisionInstances,
        decisionXml: mockDecisionXml,
      }),
    );

    await decisionsPage.gotoDecisionsPage({
      searchParams: {
        evaluated: 'true',
        failed: 'true',
        name: 'invoiceClassification',
        version: '2',
      },
    });

    await decisionsPage.displayOptionalFilter('Process Instance Key');
    await decisionsPage.displayOptionalFilter('Decision Instance Key(s)');
    await decisionsPage.decisionInstanceKeysFilter.type('aaa');
    await decisionsPage.displayOptionalFilter('Evaluation Date Range');

    await expect(page).toHaveScreenshot();
  });
});
