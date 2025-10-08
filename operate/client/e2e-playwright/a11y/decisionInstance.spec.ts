/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../visual-fixtures';
import {
  mockEvaluatedDecisionInstance,
  mockResponses,
  mockEvaluatedDecisionInstancesSearch,
  mockEvaluatedXml,
  mockFailedDecisionInstance,
  mockFailedDecisionInstancesSearch,
  mockFailedXml,
  mockEvaluatedDecisionInstanceWithoutPanels,
  mockEvaluatedDecisionInstancesSearchWithoutPanels,
  mockEvaluatedXmlWithoutPanels,
} from '../mocks/decisionInstance.mocks';
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

test.describe('decision detail', () => {
  test('have no violations for evaluated decision', async ({
    page,
    decisionInstancePage,
    makeAxeBuilder,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionInstanceDetail: mockEvaluatedDecisionInstance,
        decisionInstancesSearch: mockEvaluatedDecisionInstancesSearch,
        xml: mockEvaluatedXml,
      }),
    );

    await decisionInstancePage.gotoDecisionInstance({
      decisionInstanceKey: '1',
    });

    const results = await makeAxeBuilder()
      .exclude('.tjs-table-container')
      .analyze();

    validateResults(results);
  });

  test('have no violations for an incident', async ({
    page,
    decisionInstancePage,
    makeAxeBuilder,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionInstanceDetail: mockFailedDecisionInstance,
        decisionInstancesSearch: mockFailedDecisionInstancesSearch,
        xml: mockFailedXml,
      }),
    );

    await decisionInstancePage.gotoDecisionInstance({
      decisionInstanceKey: '1',
    });

    const results = await makeAxeBuilder()
      .exclude('.tjs-table-container')
      .analyze();

    validateResults(results);
  });

  test('have no violations for a decision without input output panels', async ({
    page,
    decisionInstancePage,
    makeAxeBuilder,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        decisionInstanceDetail: mockEvaluatedDecisionInstanceWithoutPanels,
        decisionInstancesSearch: mockEvaluatedDecisionInstancesSearchWithoutPanels,
        xml: mockEvaluatedXmlWithoutPanels,
      }),
    );

    await decisionInstancePage.gotoDecisionInstance({
      decisionInstanceKey: '1',
    });

    const results = await makeAxeBuilder()
      .exclude('.tjs-table-container')
      .analyze();

    validateResults(results);
  });
});
