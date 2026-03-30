/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '../visual-fixtures';
import {mockAuditLogs, mockResponses} from '../mocks/auditLog.mocks';
import {URL_API_PATTERN} from '../constants';
import {clientConfigMock} from '../mocks/clientConfig';
import {takePercySnapshot} from '../utils/percy';

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

test.describe('audit log page', () => {
  test('empty page', async ({page, operationsLogPage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        auditLogs: {
          items: [],
          page: {
            totalItems: 0,
            startCursor: null,
            endCursor: null,
            hasMoreTotalItems: false,
          },
        },
      }),
    );

    await operationsLogPage.gotoOperationsLogPage();

    await expect(operationsLogPage.tableLoader).not.toBeVisible();
    await expect(page.getByText('No operation log items yet')).toBeVisible();

    await expect(page).toHaveScreenshot();
    await takePercySnapshot(page, 'Audit Log - empty page');
  });

  test('error page', async ({page, operationsLogPage}) => {
    await page.route(URL_API_PATTERN, mockResponses({}));

    await operationsLogPage.gotoOperationsLogPage();

    await expect(operationsLogPage.tableLoader).not.toBeVisible();
    await expect(page.getByText('Data could not be fetched')).toBeVisible();

    await expect(page).toHaveScreenshot();
    await takePercySnapshot(page, 'Audit Log - error page');
  });

  test('filled with data', async ({page, operationsLogPage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({auditLogs: mockAuditLogs}),
    );

    await operationsLogPage.gotoOperationsLogPage();

    await expect(operationsLogPage.operationsLogTable).toBeVisible();
    await expect(operationsLogPage.tableLoader).not.toBeVisible();

    await expect(page).toHaveScreenshot();
    await takePercySnapshot(page, 'Audit Log - filled with data');
  });

  test('filtered by process instance key', async ({
    page,
    operationsLogPage,
  }) => {
    const processInstanceKey = '6755399441062827';

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        auditLogs: {
          items: mockAuditLogs.items.filter(
            (item) => item.processInstanceKey === processInstanceKey,
          ),
          page: {
            totalItems: 2,
            startCursor: null,
            endCursor: null,
            hasMoreTotalItems: false,
          },
        },
      }),
    );

    await operationsLogPage.gotoOperationsLogPage({
      searchParams: {processInstanceKey},
    });

    await expect(operationsLogPage.operationsLogTable).toBeVisible();
    await expect(operationsLogPage.tableLoader).not.toBeVisible();
    await expect(operationsLogPage.processInstanceKeyFilter).toHaveValue(
      processInstanceKey,
    );
    await page.getByTestId('data-table-loader').waitFor({state: 'detached'});

    await expect(page).toHaveScreenshot();
    await takePercySnapshot(page, 'Audit Log - filtered by process instance key');
  });
});
