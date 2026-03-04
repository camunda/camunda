/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup} from './auditLog.mocks';
import {test} from '../e2e-fixtures';
import {expect} from '@playwright/test';
import {config} from '../config';
import {SETUP_WAITING_TIME} from './constants';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  test.setTimeout(SETUP_WAITING_TIME);
  initialData = await setup();

  // Wait until the process instance is available in Operate
  await expect
    .poll(
      async () => {
        const response = await request.get(
          `${config.endpoint}/v2/process-instances/${initialData.instance.processInstanceKey}`,
        );
        return response.status();
      },
      {timeout: SETUP_WAITING_TIME},
    )
    .toBe(200);

  // Wait until audit log entries are available for the process instance
  await expect
    .poll(
      async () => {
        const response = await request.post(
          `${config.endpoint}/v2/audit-logs/search`,
          {
            data: {
              filter: {
                processInstanceKey: {
                  $eq: initialData.instance.processInstanceKey,
                },
              },
            },
          },
        );
        const body = await response.json();
        return body?.page?.totalItems ?? 0;
      },
      {timeout: SETUP_WAITING_TIME},
    )
    .toBeGreaterThan(0);
});

test.describe('Audit Log (Operations Log)', () => {
  test('Audit log entries are visible in the Operations Log', async ({
    page,
    operationsLogPage,
  }) => {
    await operationsLogPage.gotoOperationsLogPage();

    await expect(
      page.getByRole('heading', {name: /operations log/i}),
    ).toBeVisible();

    // Table column headers should be visible
    await expect(
      page.getByRole('columnheader', {name: 'Operation type'}),
    ).toBeVisible();
    await expect(
      page.getByRole('columnheader', {name: 'Entity type'}),
    ).toBeVisible();
    await expect(
      page.getByRole('columnheader', {name: 'Entity key'}),
    ).toBeVisible();
    await expect(page.getByRole('columnheader', {name: 'Actor'})).toBeVisible();
    await expect(page.getByRole('columnheader', {name: 'Date'})).toBeVisible();

    // At least one audit log data row should be visible (header row + data rows)
    await expect
      .poll(
        async () =>
          operationsLogPage.operationsLogTable.getByRole('row').count(),
        {timeout: SETUP_WAITING_TIME},
      )
      .toBeGreaterThan(1);
  });

  test('Audit log entries can be filtered by process instance key', async ({
    page,
    operationsLogPage,
  }) => {
    const processInstanceKey = initialData.instance.processInstanceKey;

    await operationsLogPage.gotoOperationsLogPage({
      searchParams: {processInstanceKey},
    });

    await expect(
      page.getByRole('heading', {name: /operations log/i}),
    ).toBeVisible();

    // At least one row related to our process instance should be visible
    await expect
      .poll(
        async () => {
          return operationsLogPage.operationsLogTable.getByRole('row').count();
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBeGreaterThan(1);

    // The process instance key filter should show the value
    await expect(operationsLogPage.processInstanceKeyFilter).toHaveValue(
      processInstanceKey,
    );

    // Verify the table contains audit log entries related to the process instance
    await expect(
      operationsLogPage.operationsLogTable
        .getByRole('cell', {name: /process instance/i})
        .first(),
    ).toBeVisible();
  });

  test('Audit log page title is correct', async ({page, operationsLogPage}) => {
    await operationsLogPage.gotoOperationsLogPage();
    await expect(page).toHaveTitle('Operate: Operations Log');
  });

  test('Audit log shows empty state when no results match filters', async ({
    page,
    operationsLogPage,
  }) => {
    // Use a non-existent process instance key to get empty results
    await operationsLogPage.gotoOperationsLogPage({
      searchParams: {processInstanceKey: '0'},
    });

    await expect(page.getByText('No operations log found')).toBeVisible();
  });
});
