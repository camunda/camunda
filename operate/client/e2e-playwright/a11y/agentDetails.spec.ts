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
  agentHistoryResponse,
  agentInstancesResponse,
  mockResponses,
  runningInstance,
} from '../mocks/processInstance';
import {validateResults} from './validateResults';
import {URL_API_PATTERN} from '../constants';
import {clientConfigMock} from '../mocks/clientConfig';

// Rules disabled to match the existing process-instance a11y baseline; these are
// tracked upstream in Carbon (https://github.com/carbon-design-system/carbon/issues/14944).
const DISABLED_RULES = ['aria-required-parent', 'list'];

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

test.describe('AI agent details', () => {
  test('have no violations for the agent details panel and conversation history', async ({
    page,
    processInstancePage,
    makeAxeBuilder,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: runningInstance.detail,
        callHierarchy: runningInstance.callHierarchy,
        elementInstances: runningInstance.elementInstances,
        statistics: runningInstance.statistics,
        sequenceFlows: runningInstance.sequenceFlows,
        variables: runningInstance.variables,
        xml: runningInstance.xml,
        agentInstances: agentInstancesResponse,
        agentHistory: agentHistoryResponse,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({key: '1'});

    await expect(page.getByRole('switch', {name: /end date/i})).toBeEnabled();

    // Selecting the agent element opens the Details tab, which renders the
    // AI Agent panel because the agent-instances search returns a match.
    await processInstancePage.diagram.clickElement('Signal user task');

    const agentDetails = page.getByTestId('agent-details');
    await expect(agentDetails).toBeVisible();
    await expect(page.getByTestId('agent-status-section')).toBeVisible();
    await expect(page.getByTestId('agent-usage-section')).toBeVisible();
    await expect(agentDetails.getByText(/thinking/i).first()).toBeVisible();

    const results = await makeAxeBuilder()
      .disableRules(DISABLED_RULES)
      .analyze();
    validateResults(results);

    // Expand the full conversation history and re-scan.
    await page.getByRole('button', {name: /conversation history/i}).click();
    await expect(page.getByText('How do I reset my password?')).toBeVisible();

    const resultsWithConversationHistory = await makeAxeBuilder()
      .disableRules(DISABLED_RULES)
      .analyze();
    validateResults(resultsWithConversationHistory);
  });
});
