/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {clientConfigMock} from '@/mocks/clientConfig';
import {test} from '@/visual-fixtures';
import {
  mockResponses,
  agentProcessWithOneActiveInstance,
  agentProcessWithTwoActiveInstances,
  type InstanceMock,
} from '@/mocks/processInstance';
import {URL_API_PATTERN} from '@/constants';

const AI_AGENT_ELEMENT_ID = 'ai_agent';
const AI_AGENT_ELEMENT_INSTANCE_KEY =
  agentProcessWithOneActiveInstance.elementInstances.items[1]!
    .elementInstanceKey;
const PROCESS_INSTANCE_KEY =
  agentProcessWithOneActiveInstance.detail.processInstanceKey;

function agentMockResponses(mock: InstanceMock) {
  return mockResponses({
    processInstanceDetail: mock.detail,
    callHierarchy: mock.callHierarchy,
    elementInstances: mock.elementInstances,
    statistics: mock.statistics,
    sequenceFlows: mock.sequenceFlows,
    variables: mock.variables,
    xml: mock.xml,
    agentInstances: mock.agentInstances,
    agentInstanceHistory: mock.agentInstanceHistory,
  });
}

test.describe('AI agent details', () => {
  // Taller viewport is needed to fix all agent details into the page.
  test.use({viewport: {width: 1280, height: 1400}});

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

    // Increased bottom panel height to have more details visible in screenshots.
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

  test('loading error', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      agentMockResponses(agentProcessWithOneActiveInstance),
    );
    await page.route('**/v2/agent-instances/search', (route) =>
      route.fulfill({
        status: 500,
        body: JSON.stringify({}),
        headers: {'content-type': 'application/json'},
      }),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: PROCESS_INSTANCE_KEY,
      bottomPanel: 'details',
      selection: `elementId=${AI_AGENT_ELEMENT_ID}`,
    });

    await expect(processInstancePage.aiAgentDetails.errorMessage).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('sections with agent instance information', async ({
    page,
    processInstancePage,
  }) => {
    await page.route(
      URL_API_PATTERN,
      agentMockResponses(agentProcessWithOneActiveInstance),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: PROCESS_INSTANCE_KEY,
      bottomPanel: 'details',
      selection: `elementId=${AI_AGENT_ELEMENT_ID}`,
    });

    await processInstancePage.aiAgentDetails.usageSectionTrigger.click();
    await processInstancePage.aiAgentDetails.systemPromptSection.click();
    await processInstancePage.aiAgentDetails.availableToolsSection.click();
    await processInstancePage.aiAgentDetails.modelSection.click();

    await expect(
      processInstancePage.aiAgentDetails.statusSection.getByLabel(
        'Assistant message',
      ),
    ).toBeVisible();

    await expect(
      processInstancePage.aiAgentDetails.statusOverlay,
    ).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('conversation history section', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      agentMockResponses(agentProcessWithOneActiveInstance),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: PROCESS_INSTANCE_KEY,
      bottomPanel: 'details',
      selection: `elementId=${AI_AGENT_ELEMENT_ID}`,
    });

    await processInstancePage.aiAgentDetails.conversationHistorySectionTrigger.click();

    await expect(
      processInstancePage.aiAgentDetails.conversationHistorySection.getByLabel(
        'User message',
      ),
    ).toBeVisible();
    await expect(
      processInstancePage.aiAgentDetails.statusOverlay,
    ).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('conversation message details modal', async ({
    page,
    processInstancePage,
  }) => {
    await page.route(
      URL_API_PATTERN,
      agentMockResponses(agentProcessWithOneActiveInstance),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: PROCESS_INSTANCE_KEY,
      bottomPanel: 'details',
      selection: `elementId=${AI_AGENT_ELEMENT_ID}`,
    });

    await processInstancePage.aiAgentDetails.conversationHistorySectionTrigger.click();
    await processInstancePage.aiAgentDetails.conversationHistorySection
      .getByLabel('Assistant message')
      .nth(1)
      .getByRole('button', {name: 'Expand'})
      .click();

    const modal = page.getByRole('dialog', {name: 'Assistant message'});
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/order #12345/)).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('conversation tool result modal', async ({
    page,
    processInstancePage,
  }) => {
    await page.route(
      URL_API_PATTERN,
      agentMockResponses(agentProcessWithOneActiveInstance),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: PROCESS_INSTANCE_KEY,
      bottomPanel: 'details',
      selection: `elementId=${AI_AGENT_ELEMENT_ID}`,
    });

    await processInstancePage.aiAgentDetails.conversationHistorySectionTrigger.click();
    await processInstancePage.aiAgentDetails.conversationHistorySection
      .getByLabel('Result for "get_order_status" tool call')
      .getByRole('button', {name: 'Expand'})
      .click();

    const modal = page.getByRole('dialog', {
      name: 'Tool call: get_order_status',
    });
    await expect(modal).toBeVisible();

    // Wait for preview editors to load.
    await expect(
      modal.getByTestId('tool-call-input').getByText('"orderId"'),
    ).toBeVisible();
    await expect(
      modal.getByTestId('tool-call-output').getByText('"trackingNumber"'),
    ).toBeVisible();
    await processInstancePage.previewEditor.hideCaret();

    await expect(page).toHaveScreenshot();
  });

  test('scoped conversation history section', async ({
    page,
    processInstancePage,
  }) => {
    await page.route(
      URL_API_PATTERN,
      agentMockResponses(agentProcessWithOneActiveInstance),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: PROCESS_INSTANCE_KEY,
      bottomPanel: 'details',
      selection: `elementId=${AI_AGENT_ELEMENT_ID}&elementInstanceKey=${AI_AGENT_ELEMENT_INSTANCE_KEY}`,
    });

    await processInstancePage.aiAgentDetails.conversationHistorySectionTrigger.click();

    await expect(
      processInstancePage.aiAgentDetails.conversationHistorySection.getByLabel(
        'Assistant message',
      ),
    ).toBeVisible();
    await expect(
      processInstancePage.aiAgentDetails.statusOverlay,
    ).toBeVisible();

    await expect(page).toHaveScreenshot();
  });

  test('multiple active agents', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      agentMockResponses(agentProcessWithTwoActiveInstances),
    );

    await processInstancePage.gotoProcessInstancePage({
      key: PROCESS_INSTANCE_KEY,
      bottomPanel: 'details',
      selection: `elementId=${AI_AGENT_ELEMENT_ID}`,
    });

    await expect(
      processInstancePage.aiAgentDetails.statusOverlay,
    ).toBeVisible();

    await processInstancePage.aiAgentDetails.agentSelector.click();

    await expect(page).toHaveScreenshot();
  });
});
