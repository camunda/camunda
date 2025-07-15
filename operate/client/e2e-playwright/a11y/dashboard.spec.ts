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
  mockIncidentsByError,
  mockIncidentsByProcess,
  mockStatistics,
  mockResponses,
} from '../mocks/dashboard.mocks';
import {validateResults} from './validateResults';
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

test.describe('dashboard', () => {
  test('have no violations', async ({page, dashboardPage, makeAxeBuilder}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        statistics: mockStatistics,
        incidentsByError: mockIncidentsByError,
        incidentsByProcess: mockIncidentsByProcess,
      }),
    );

    await dashboardPage.gotoDashboardPage();

    const results = await makeAxeBuilder().analyze();

    validateResults(results);
  });

  test('have no violations when rows are expanded', async ({
    page,
    dashboardPage,
    makeAxeBuilder,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        statistics: mockStatistics,
        incidentsByError: mockIncidentsByError,
        incidentsByProcess: mockIncidentsByProcess,
      }),
    );

    await dashboardPage.gotoDashboardPage();

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
});
