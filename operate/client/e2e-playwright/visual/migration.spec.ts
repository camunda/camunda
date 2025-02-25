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
  mockBatchOperations,
  mockGroupedProcesses,
  mockProcessInstances,
  mockResponses,
  mockStatistics,
} from '../mocks/processes.mocks';
import {open} from 'modules/mocks/diagrams';
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

test.describe('migration view', () => {
  for (const theme of ['light', 'dark']) {
    test(`initial migration view - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isOperationsCollapsed: true,
          }),
        );
      }, theme);

      await page.route(
        URL_API_PATTERN,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstances,
          statistics: mockStatistics,
          processXml: open('LotsOfTasks.bpmn'),
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
          process: 'LotsOfTasks',
          version: '1',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await processesPage.getNthProcessInstanceCheckbox(0).click();
      await processesPage.migrateButton.click();
      await processesPage.migrationModal.confirmButton.click();

      await expect(page).toHaveScreenshot();
    });
  }
});
