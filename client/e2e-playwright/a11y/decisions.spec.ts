/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import {
  mockDecisionInstances,
  mockGroupedDecisions,
  mockBatchOperations,
  mockResponses,
} from '../mocks/decisions.mocks';

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
        /^.*\/api.*$/i,
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

      expect(results.violations).toHaveLength(0);
      expect(results.passes.length).toBeGreaterThan(0);
    });
  }
});
