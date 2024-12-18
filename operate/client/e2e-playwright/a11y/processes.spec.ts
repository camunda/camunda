/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../test-fixtures';
import {
  mockBatchOperations,
  mockGroupedProcesses,
  mockProcessInstances,
  mockProcessXml,
  mockStatistics,
  mockResponses,
} from '../mocks/processes.mocks';
import {validateResults} from './validateResults';
import {URL_PATTERN} from '../constants';

test.describe('processes', () => {
  for (const theme of ['light', 'dark']) {
    test(`have no violations in ${theme} theme`, async ({
      page,
      commonPage,
      processesPage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);
      await page.route(
        URL_PATTERN,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstances,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {active: 'true', incidents: 'true'},
        options: {waitUntil: 'networkidle'},
      });

      const results = await makeAxeBuilder().analyze();

      validateResults(results);
    });
  }
});
