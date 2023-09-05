/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect, Route} from '@playwright/test';
import {test} from '../test-fixtures';
import {Paths} from 'modules/Routes';
import {IncidentByErrorDto} from 'modules/api/incidents/fetchIncidentsByError';
import {ProcessInstanceByNameDto} from 'modules/api/incidents/fetchProcessInstancesByName';
import {CoreStatisticsDto} from 'modules/api/processInstances/fetchProcessCoreStatistics';
import {
  mockIncidentsByError,
  mockIncidentsByProcess,
  mockStatistics,
} from './dashboard.mocks';

function mockResponses({
  statistics,
  incidentsByError,
  incidentsByProcess,
}: {
  statistics?: CoreStatisticsDto;
  incidentsByError?: IncidentByErrorDto[];
  incidentsByProcess?: ProcessInstanceByNameDto[];
}) {
  return (route: Route) => {
    if (route.request().url().includes('/api/authentications/user')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          userId: 'demo',
          displayName: 'demo',
          canLogout: true,
          permissions: ['read', 'write'],
          roles: null,
          salesPlanType: null,
          c8Links: {},
          username: 'demo',
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (
      route.request().url().includes('/api/process-instances/core-statistics')
    ) {
      return route.fulfill({
        status: statistics === undefined ? 400 : 200,
        body: JSON.stringify(statistics),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/incidents/byError')) {
      return route.fulfill({
        status: incidentsByError === undefined ? 400 : 200,
        body: JSON.stringify(incidentsByError),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/api/incidents/byProcess')) {
      return route.fulfill({
        status: incidentsByProcess === undefined ? 400 : 200,
        body: JSON.stringify(incidentsByProcess),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    route.continue();
  };
}

test.describe('dashboard page', () => {
  for (const theme of ['light', 'dark']) {
    test(`empty page - ${theme}`, async ({page, commonPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          statistics: {
            running: 0,
            active: 0,
            withIncidents: 0,
          },
          incidentsByError: [],
          incidentsByProcess: [],
        }),
      );

      await page.goto(Paths.dashboard(), {
        waitUntil: 'networkidle',
      });

      await expect(page).toHaveScreenshot();
    });

    test(`error page - ${theme}`, async ({page, commonPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(/^.*\/api.*$/i, mockResponses({}));

      await page.goto(Paths.dashboard(), {
        waitUntil: 'networkidle',
      });

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data - ${theme}`, async ({page, commonPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          statistics: mockStatistics,
          incidentsByError: mockIncidentsByError,
          incidentsByProcess: mockIncidentsByProcess,
        }),
      );

      await page.goto(Paths.dashboard(), {
        waitUntil: 'networkidle',
      });

      await expect(page).toHaveScreenshot();
    });

    test(`expanded rows - ${theme}`, async ({page, commonPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          statistics: mockStatistics,
          incidentsByError: mockIncidentsByError,
          incidentsByProcess: mockIncidentsByProcess,
        }),
      );

      await page.goto(Paths.dashboard(), {
        waitUntil: 'networkidle',
      });

      const expandInstancesByProcessRow = page
        .getByTestId('instances-by-process')
        .getByRole('button', {
          name: /expand current row/i,
        })
        .nth(0);

      expect(expandInstancesByProcessRow).toBeEnabled();

      await expandInstancesByProcessRow.click();

      await expect(
        page.getByText(/order process – 136 instances in version 2/i),
      ).toBeVisible();

      const expandIncidentsByErrorRow = page
        .getByTestId('incident-byError')
        .getByRole('button', {
          name: /expand current row/i,
        })
        .nth(0);

      await expandIncidentsByErrorRow.click();

      await expect(page.getByText(/complexprocess – version 2/i)).toBeVisible();

      await expect(page).toHaveScreenshot();
    });
  }
});
