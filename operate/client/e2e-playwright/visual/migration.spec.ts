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
  mockBatchOperations,
  mockProcessDefinitions,
  mockProcessInstances,
  mockResponses,
  mockStatistics,
} from '../mocks/processes.mocks';
import {openFile} from '@/utils/openFile';
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
  test('initial migration view', async ({page, processesPage}) => {
    await page.addInitScript(`() => {
      window.localStorage.setItem(
        'panelStates',
        JSON.stringify({
          isOperationsCollapsed: true,
        }),
      );
    }`);

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processDefinitions: mockProcessDefinitions,
        batchOperations: mockBatchOperations,
        processInstances: mockProcessInstances,
        batchOperationItems: {items: [], page: {totalItems: 0}},
        statistics: mockStatistics,
        processXml: openFile(
          './e2e-playwright/mocks/resources/LotsOfTasks.bpmn',
        ),
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {
        active: 'true',
        incidents: 'true',
        process: 'LotsOfTasks',
        version: '1',
      },
    });

    await processesPage.getNthProcessInstanceCheckbox(0).click();
    await processesPage.migrateButton.click();
    await processesPage.migrationModal.confirmButton.click();

    await expect(page).toHaveScreenshot();
  });
});
