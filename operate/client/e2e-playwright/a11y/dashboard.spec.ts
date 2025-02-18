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
  mockStatistics,
  mockResponses,
} from '../mocks/dashboard.mocks';
import {Paths} from 'modules/Routes';
import {validateResults} from './validateResults';
import {URL_API_PATTERN} from '../constants';

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
        URL_API_PATTERN,
        mockResponses({
          statistics: mockStatistics,
          incidentsByError: mockIncidentsByError,
          incidentsByProcess: mockIncidentsByProcess,
        }),
      );

      await dashboardPage.navigateToDashboard({waitUntil: 'networkidle'});

      const results = await makeAxeBuilder().analyze();

      validateResults(results);
    });

    test(`have no violations when rows are expanded in ${theme} theme`, async ({
      page,
      commonPage,
      makeAxeBuilder,
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

      const results = await makeAxeBuilder()
        // TODO: enable 'color-contrast' rule when the related TODO item is fixed https://github.com/camunda/operate/issues/5027
        .disableRules(['color-contrast'])
        .analyze();

      validateResults(results);
    });
  }
});
