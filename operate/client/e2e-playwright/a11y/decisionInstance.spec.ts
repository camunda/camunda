/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '../test-fixtures';
import {
  mockEvaluatedDecisionInstance,
  mockResponses,
  mockEvaluatedDrdData,
  mockEvaluatedXml,
  mockFailedDecisionInstance,
  mockFailedDrdData,
  mockFailedXml,
  mockEvaluatedDecisionInstanceWithoutPanels,
  mockEvaluatedDrdDataWithoutPanels,
  mockEvaluatedXmlWithoutPanels,
} from '../mocks/decisionInstance.mocks';
import {Paths} from 'modules/Routes';
import {validateResults} from './validateResults';
import {URL_PATTERN} from '../constants';

test.describe('decision detail', () => {
  for (const theme of ['light', 'dark']) {
    test(`have no violations for evaluated decision in ${theme} theme`, async ({
      page,
      commonPage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_PATTERN,
        mockResponses({
          decisionInstanceDetail: mockEvaluatedDecisionInstance,
          drdData: mockEvaluatedDrdData,
          xml: mockEvaluatedXml,
        }),
      );

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      const results = await makeAxeBuilder()
        .exclude('.tjs-table-container')
        .analyze();

      validateResults(results);
    });

    test(`have no violations for an incident in ${theme} theme`, async ({
      page,
      commonPage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_PATTERN,
        mockResponses({
          decisionInstanceDetail: mockFailedDecisionInstance,
          drdData: mockFailedDrdData,
          xml: mockFailedXml,
        }),
      );

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      const results = await makeAxeBuilder()
        .exclude('.tjs-table-container')
        .analyze();

      validateResults(results);
    });

    test(`have no violations for a decision without input output panels in ${theme} theme`, async ({
      page,
      commonPage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        URL_PATTERN,
        mockResponses({
          decisionInstanceDetail: mockEvaluatedDecisionInstanceWithoutPanels,
          drdData: mockEvaluatedDrdDataWithoutPanels,
          xml: mockEvaluatedXmlWithoutPanels,
        }),
      );

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      const results = await makeAxeBuilder()
        .exclude('.tjs-table-container')
        .analyze();

      validateResults(results);
    });
  }
});
