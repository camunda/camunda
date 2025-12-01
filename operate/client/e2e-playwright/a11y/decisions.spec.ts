/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../visual-fixtures';
import {
  mockDecisionInstances,
  mockedDecisionDefinitions,
  mockBatchOperations,
  mockResponses,
  mockDecisionXml,
} from '../mocks/decisions.mocks';
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

test.describe('decisions', () => {
  test('have no violations', async ({page, decisionsPage, makeAxeBuilder}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionDefinitions: mockedDecisionDefinitions,
        batchOperations: mockBatchOperations,
        decisionInstances: mockDecisionInstances,
      }),
    );

    await decisionsPage.gotoDecisionsPage({
      searchParams: {evaluated: 'true', failed: 'true'},
    });

    const results = await makeAxeBuilder().analyze();

    validateResults(results);
  });

  test('have no violations when a decision is selected', async ({
    page,
    decisionsPage,
    makeAxeBuilder,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionDefinitions: mockedDecisionDefinitions,
        batchOperations: mockBatchOperations,
        decisionInstances: mockDecisionInstances,
        decisionXml: mockDecisionXml,
      }),
    );

    await decisionsPage.gotoDecisionsPage({
      searchParams: {
        evaluated: 'true',
        failed: 'true',
        name: 'invoiceClassification',
        version: '2',
      },
    });

    const results = await makeAxeBuilder()
      .exclude('.tjs-table-container')
      .analyze();

    validateResults(results);
  });
});
