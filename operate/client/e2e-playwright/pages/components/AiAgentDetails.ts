/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Locator, Page} from '@playwright/test';

class AiAgentDetails {
  readonly container: Locator;
  readonly statusOverlay: Locator;
  readonly errorMessage: Locator;
  readonly agentSelector: Locator;
  readonly statusSection: Locator;
  readonly usageSection: Locator;
  readonly conversationHistorySection: Locator;
  readonly systemPromptSection: Locator;
  readonly availableToolsSection: Locator;
  readonly modelSection: Locator;
  readonly statusSectionTrigger: Locator;
  readonly usageSectionTrigger: Locator;
  readonly conversationHistorySectionTrigger: Locator;
  readonly systemPromptSectionTrigger: Locator;
  readonly availableToolsSectionTrigger: Locator;
  readonly modelSectionTrigger: Locator;

  constructor(page: Page) {
    this.container = page.getByRole('article', {name: 'AI Agent'});
    this.statusOverlay = page.getByTestId(/^agent-status-overlay-/).first();

    this.errorMessage = this.container.getByText(
      'Unable to load agent information.',
    );
    this.agentSelector = this.container.getByRole('combobox', {
      name: 'Current AI agent',
    });

    this.statusSection = this.container.getByTestId('agent-status-section');
    this.usageSection = this.container.getByTestId('agent-usage-section');
    this.conversationHistorySection = this.container.getByTestId(
      'agent-conversation-history-section',
    );
    this.systemPromptSection = this.container.getByTestId(
      'agent-system-prompt-section',
    );
    this.availableToolsSection = this.container.getByTestId(
      'agent-available-tools-section',
    );
    this.modelSection = this.container.getByTestId('agent-model-section');

    this.statusSectionTrigger = this.container.getByRole('button', {
      name: 'Status:',
    });
    this.usageSectionTrigger = this.container.getByRole('button', {
      name: 'Usage',
    });
    this.conversationHistorySectionTrigger = this.container.getByRole(
      'button',
      {name: 'Conversation history'},
    );
    this.systemPromptSectionTrigger = this.container.getByRole('button', {
      name: 'System prompt',
    });
    this.availableToolsSectionTrigger = this.container.getByRole('button', {
      name: 'Available tools',
    });
    this.modelSectionTrigger = this.container.getByRole('button', {
      name: 'Model',
      exact: true,
    });
  }
}

export {AiAgentDetails};
