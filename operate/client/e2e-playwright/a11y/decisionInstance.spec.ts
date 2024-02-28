/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

test.describe('decision detail', () => {
  for (const theme of ['light', 'dark']) {
    test(`have no violations for evaluated decision in ${theme} theme`, async ({
      page,
      commonPage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          decisionInstanceDetail: mockEvaluatedDecisionInstance,
          drdData: mockEvaluatedDrdData,
          xml: mockEvaluatedXml,
        }),
      );

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      const results = await makeAxeBuilder().analyze();

      validateResults(results);
    });

    test(`have no violations for an incident in ${theme} theme`, async ({
      page,
      commonPage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          decisionInstanceDetail: mockFailedDecisionInstance,
          drdData: mockFailedDrdData,
          xml: mockFailedXml,
        }),
      );

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      const results = await makeAxeBuilder().analyze();

      validateResults(results);
    });

    test(`have no violations for a decision without input output panels in ${theme} theme`, async ({
      page,
      commonPage,
      makeAxeBuilder,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          decisionInstanceDetail: mockEvaluatedDecisionInstanceWithoutPanels,
          drdData: mockEvaluatedDrdDataWithoutPanels,
          xml: mockEvaluatedXmlWithoutPanels,
        }),
      );

      await page.goto(Paths.decisionInstance('1'), {
        waitUntil: 'networkidle',
      });

      const results = await makeAxeBuilder().analyze();

      validateResults(results);
    });
  }
});
