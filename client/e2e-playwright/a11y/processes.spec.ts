/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import {Paths} from 'modules/Routes';
import {
  mockBatchOperations,
  mockGroupedProcesses,
  mockProcessInstances,
  mockProcessXml,
  mockStatistics,
  mockResponses,
} from '../mocks/processes.mocks';

test.describe('processes', () => {
  for (const theme of ['light', 'dark']) {
    test(`have no violations in ${theme} theme`, async ({
      page,
      commonPage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);
      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstances,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await page.goto(`${Paths.processes()}?active=true&incidents=true`, {
        waitUntil: 'networkidle',
      });

      const results = await makeAxeBuilder().analyze();

      expect(results.violations).toHaveLength(0);
      expect(results.passes.length).toBeGreaterThan(0);
    });
  }
});
