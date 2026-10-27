/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../visual-fixtures';
import {
  mockBatchOperations,
  mockProcessDefinitions,
  mockProcessInstances,
  mockProcessXml,
  mockResponses,
  mockStatistics,
} from '../mocks/processes.mocks';
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

test.describe('processes', () => {
  test('have no violations', async ({page, processesPage, makeAxeBuilder}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processDefinitions: mockProcessDefinitions,
        batchOperations: mockBatchOperations,
        processInstances: mockProcessInstances,
        statistics: mockStatistics,
        processXml: mockProcessXml,
      }),
    );

    await processesPage.gotoProcessesPage({
      searchParams: {active: 'true', incidents: 'true'},
    });

    const results = await makeAxeBuilder().analyze();

    validateResults(results);
  });
});
