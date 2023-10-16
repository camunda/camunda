/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {test} from '../test-fixtures';
import {
  mockIncidentsByError,
  mockIncidentsByProcess,
  mockStatistics as mockDashboardStatistics,
  mockResponses as mockDashboardResponses,
} from '../mocks/dashboard.mocks';

test.beforeEach(async ({page, commonPage, context}) => {
  await commonPage.mockClientConfig(context);
  await page.setViewportSize({width: 1650, height: 900});
});

test.describe('self managed platform deployment', () => {
  test('view dashboard with no processes', async ({page, dashboardPage}) => {
    await page.route(
      /^.*\/api.*$/i,
      mockDashboardResponses({
        statistics: {running: 0, withIncidents: 0, active: 0},
        incidentsByError: [],
        incidentsByProcess: [],
      }),
    );

    await dashboardPage.navigateToDashboard({waitUntil: 'networkidle'});

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/self-managed-platform-deployment/operate-dashboard-no-processes.png',
    });
  });

  test('view dashboard with processes', async ({page, dashboardPage}) => {
    await page.route(
      /^.*\/api.*$/i,
      mockDashboardResponses({
        statistics: mockDashboardStatistics,
        incidentsByError: mockIncidentsByError,
        incidentsByProcess: mockIncidentsByProcess,
      }),
    );

    await dashboardPage.navigateToDashboard({waitUntil: 'networkidle'});

    await page.screenshot({
      path: 'e2e-playwright/docs-screenshots/self-managed-platform-deployment/operate-introduction.png',
    });
  });
});
