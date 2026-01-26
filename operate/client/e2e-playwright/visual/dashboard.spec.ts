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
  mockProcessDefinitionStatistics,
  mockResponses,
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
  test('empty page', async ({page, dashboardPage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        incidentsByError: {items: [], page: {totalItems: 0}},
        processDefinitionStatistics: {items: [], page: {totalItems: 0}},
      }),
    );

    await dashboardPage.gotoDashboardPage();

    await expect(page).toHaveScreenshot();
  });

  test('error page', async ({page, dashboardPage}) => {
    await page.route(URL_API_PATTERN, mockResponses({}));

    await dashboardPage.gotoDashboardPage();

    await expect(page.getByText('Data could not be fetched')).toHaveCount(2);
    await expect(
      page.getByText('Process statistics could not be fetched'),
    ).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('filled with data', async ({page, dashboardPage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        incidentsByError: mockIncidentsByError,
        processDefinitionStatistics: mockProcessDefinitionStatistics,
      }),
    );

    await dashboardPage.gotoDashboardPage();

    await expect(page).toHaveScreenshot();
  });

  test('expanded rows', async ({page, dashboardPage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        incidentsByError: mockIncidentsByError,
        processDefinitionStatistics: mockProcessDefinitionStatistics,
      }),
    );

    await dashboardPage.gotoDashboardPage();

    const expandInstancesByProcessRow = page
      .getByTestId('instances-by-process-definition')
      .getByRole('button', {
        name: /expand current row/i,
      })
      .nth(0);

    expect(expandInstancesByProcessRow).toBeEnabled();

    await expect(
      page.getByText(/order process – 146 instances in 2\+ versions/i),
    ).toBeVisible();

    await expandInstancesByProcessRow.click();

    await expect(
      page.getByText(/order process – 136 instances in version 2/i),
    ).toBeVisible();
    await expect(
      page.getByText(/order process – 10 instances in version 1/i),
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
});
