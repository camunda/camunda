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
  mockResponses,
  agentProcessWithOneActiveInstance,
} from '../mocks/processInstance';
import {validateResults} from './validateResults';
import {URL_API_PATTERN} from '../constants';
import {clientConfigMock} from '../mocks/clientConfig';

const AI_AGENT_ELEMENT_ID = 'ai_agent';
const PROCESS_INSTANCE_KEY =
  agentProcessWithOneActiveInstance.detail.processInstanceKey;

// Scope the axe scans to the agent details panel so violations elsewhere on the
// process instance page do not leak into this feature-focused test.
const AGENT_DETAILS_PANEL_SELECTOR = '[data-testid="agent-details"]';

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

  // Expand the bottom panel so all agent details sections render into the page.
  await context.addInitScript(() => {
    window.localStorage.setItem(
      'panelStates',
      JSON.stringify({
        'process-detail-vertical-panel': [25, 75],
        'process-instance-bottom-panel': [25, 75],
      }),
    );
  });
});

test.describe('AI agent details', () => {
  // Taller viewport is needed to fit all agent details into the page.
  test.use({viewport: {width: 1280, height: 1400}});

  test('have no violations for the agent details panel and conversation history', async ({
    page,
    processInstancePage,
    makeAxeBuilder,
  }) => {
    const mock = agentProcessWithOneActiveInstance;

    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: mock.detail,
        callHierarchy: mock.callHierarchy,
        elementInstances: mock.elementInstances,
        statistics: mock.statistics,
        sequenceFlows: mock.sequenceFlows,
        variables: mock.variables,
        xml: mock.xml,
        agentInstances: mock.agentInstances,
        agentInstanceHistory: mock.agentInstanceHistory,
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: PROCESS_INSTANCE_KEY,
      bottomPanel: 'details',
      selection: `elementId=${AI_AGENT_ELEMENT_ID}`,
    });

    // The status overlay renders once the agent-instances search returns a match.
    await expect(
      processInstancePage.aiAgentDetails.statusOverlay,
    ).toBeVisible();
    await expect(
      processInstancePage.aiAgentDetails.statusSection.getByLabel(
        'Assistant message',
      ),
    ).toBeVisible();

    const results = await makeAxeBuilder()
      .include(AGENT_DETAILS_PANEL_SELECTOR)
      .disableRules(DISABLED_RULES)
      .analyze();
    validateResults(results);

    // Expand the full conversation history and re-scan.
    await processInstancePage.aiAgentDetails.conversationHistorySectionTrigger.click();
    await expect(
      processInstancePage.aiAgentDetails.conversationHistorySection.getByText(
        'What is the status of order #12345?',
      ),
    ).toBeVisible();

    const resultsWithConversationHistory = await makeAxeBuilder()
      .include(AGENT_DETAILS_PANEL_SELECTOR)
      .disableRules(DISABLED_RULES)
      .analyze();
    validateResults(resultsWithConversationHistory);
  });
});
