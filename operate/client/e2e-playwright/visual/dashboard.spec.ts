/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import {
  mockIncidentsByError,
  mockIncidentsByProcess,
  mockResponses,
  mockStatistics,
} from '../mocks/dashboard.mocks';
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

test.describe('dashboard page', () => {
  for (const theme of ['light', 'dark']) {
    test(`empty page - ${theme}`, async ({page, commonPage, dashboardPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_API_PATTERN,
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

      await dashboardPage.navigateToDashboard({waitUntil: 'networkidle'});

      await expect(page).toHaveScreenshot();
    });

    test(`error page - ${theme}`, async ({page, commonPage, dashboardPage}) => {
      await commonPage.changeTheme(theme);

      await page.route(URL_API_PATTERN, mockResponses({}));

      await dashboardPage.navigateToDashboard({waitUntil: 'networkidle'});

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data - ${theme}`, async ({
      page,
      commonPage,
      dashboardPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_API_PATTERN,
        mockResponses({
          statistics: mockStatistics,
          incidentsByError: mockIncidentsByError,
          incidentsByProcess: mockIncidentsByProcess,
        }),
      );

      await dashboardPage.navigateToDashboard({waitUntil: 'networkidle'});

      await expect(page).toHaveScreenshot();
    });

    test(`expanded rows - ${theme}`, async ({
      page,
      commonPage,
      dashboardPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_API_PATTERN,
        mockResponses({
          statistics: mockStatistics,
          incidentsByError: mockIncidentsByError,
          incidentsByProcess: mockIncidentsByProcess,
        }),
      );

      await dashboardPage.navigateToDashboard({waitUntil: 'networkidle'});

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
