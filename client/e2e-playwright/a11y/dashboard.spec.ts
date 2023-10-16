/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import {
  mockIncidentsByError,
  mockIncidentsByProcess,
  mockStatistics,
  mockResponses,
} from '../mocks/dashboard.mocks';

test.describe('dashboard', () => {
  for (const theme of ['light', 'dark']) {
    test(`have no violations in ${theme} theme`, async ({
      page,
      commonPage,
      dashboardPage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          statistics: mockStatistics,
          incidentsByError: mockIncidentsByError,
          incidentsByProcess: mockIncidentsByProcess,
        }),
      );

      await dashboardPage.navigateToDashboard({waitUntil: 'networkidle'});

      const results = await makeAxeBuilder()
        .disableRules(['td-headers-attr'])
        .analyze();

      expect(results.violations).toHaveLength(0);
      expect(results.passes.length).toBeGreaterThan(0);
    });
  }
});
