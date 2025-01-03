/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../test-fixtures';
import {
  mockDecisionInstances,
  mockGroupedDecisions,
  mockBatchOperations,
  mockResponses,
  mockDecisionXml,
} from '../mocks/decisions.mocks';
import {validateResults} from './validateResults';
import {URL_PATTERN} from '../constants';

test.describe('decisions', () => {
  for (const theme of ['light', 'dark']) {
    test(`have no violations in ${theme} theme`, async ({
      page,
      commonPage,
      decisionsPage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_PATTERN,
        mockResponses({
          groupedDecisions: mockGroupedDecisions,
          batchOperations: mockBatchOperations,
          decisionInstances: mockDecisionInstances,
        }),
      );

      await decisionsPage.navigateToDecisions({
        searchParams: {evaluated: 'true', failed: 'true'},
        options: {waitUntil: 'networkidle'},
      });

      const results = await makeAxeBuilder().analyze();

      validateResults(results);
    });

    test(`have no violations when a decision is selected in ${theme} theme`, async ({
      page,
      commonPage,
      decisionsPage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_PATTERN,
        mockResponses({
          groupedDecisions: mockGroupedDecisions,
          batchOperations: mockBatchOperations,
          decisionInstances: mockDecisionInstances,
          decisionXml: mockDecisionXml,
        }),
      );

      await decisionsPage.navigateToDecisions({
        searchParams: {
          evaluated: 'true',
          failed: 'true',
          name: 'invoiceClassification',
          version: '2',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      const results = await makeAxeBuilder()
        .exclude('.tjs-table-container')
        .analyze();

      validateResults(results);
    });
  }
});
