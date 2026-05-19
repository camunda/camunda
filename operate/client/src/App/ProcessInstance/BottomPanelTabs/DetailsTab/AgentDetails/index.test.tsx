/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import {AgentDetails} from './index';
import type {AgentInstance} from '@camunda/camunda-api-zod-schemas/8.10';

const mockAgentInstance: AgentInstance = {
  agentInstanceKey: '2251799813851828',
  status: 'TOOL_CALLING',
  definition: {
    model: 'gpt-4',
    provider: 'openai',
    systemPrompt: 'You are a helpful assistant.',
  },
  metrics: {
    inputTokens: 100,
    outputTokens: 50,
    modelCalls: 3,
    toolCalls: 2,
  },
  limits: {
    maxModelCalls: 10,
    maxToolCalls: 5,
    maxTokens: 1000,
  },
  elementId: 'Activity_1',
  processInstanceKey: '123456789',
  processDefinitionKey: '444555666',
  tenantId: '<default>',
  creationDate: '2025-01-15T10:00:00.000Z',
  lastUpdatedDate: '2025-01-15T10:05:00.000Z',
  completionDate: null,
  elementInstanceKeys: ['111222333'],
};

describe('<AgentDetails />', () => {
  it('should render AI Agent heading and status for TOOL_CALLING', () => {
    render(
      <AgentDetails
        agentInstance={mockAgentInstance}
        isLoading={false}
        isError={false}
      />,
    );

    expect(screen.getByText('AI Agent')).toBeInTheDocument();
    expect(screen.getByText('Status: Calling tools')).toBeInTheDocument();
  });

  it('should render status for THINKING', () => {
    render(
      <AgentDetails
        agentInstance={{...mockAgentInstance, status: 'THINKING'}}
        isLoading={false}
        isError={false}
      />,
    );

    expect(screen.getByText('Status: Thinking')).toBeInTheDocument();
  });

  it('should render status for IDLE', () => {
    render(
      <AgentDetails
        agentInstance={{...mockAgentInstance, status: 'IDLE'}}
        isLoading={false}
        isError={false}
      />,
    );

    expect(screen.getByText('Status: Idle')).toBeInTheDocument();
  });

  it('should render status for COMPLETED', () => {
    render(
      <AgentDetails
        agentInstance={{...mockAgentInstance, status: 'COMPLETED'}}
        isLoading={false}
        isError={false}
      />,
    );

    expect(screen.getByText('Status: Completed')).toBeInTheDocument();
  });

  it('should render status for INITIALIZING', () => {
    render(
      <AgentDetails
        agentInstance={{...mockAgentInstance, status: 'INITIALIZING'}}
        isLoading={false}
        isError={false}
      />,
    );

    expect(screen.getByText('Status: Initializing')).toBeInTheDocument();
  });

  it('should render status for TOOL_DISCOVERY', () => {
    render(
      <AgentDetails
        agentInstance={{...mockAgentInstance, status: 'TOOL_DISCOVERY'}}
        isLoading={false}
        isError={false}
      />,
    );

    expect(screen.getByText('Status: Discovering tools')).toBeInTheDocument();
  });

  it('should render loading state', () => {
    render(
      <AgentDetails
        agentInstance={undefined}
        isLoading={true}
        isError={false}
      />,
    );

    expect(screen.getByText('AI Agent')).toBeInTheDocument();
    expect(screen.getByText('Loading...')).toBeInTheDocument();
  });

  it('should render error state when fetch fails', () => {
    render(
      <AgentDetails
        agentInstance={undefined}
        isLoading={false}
        isError={true}
      />,
    );

    expect(screen.getByText('AI Agent')).toBeInTheDocument();
    expect(screen.getByText('Unable to load agent status')).toBeInTheDocument();
  });

  it('should render usage metrics', () => {
    render(
      <AgentDetails
        agentInstance={mockAgentInstance}
        isLoading={false}
        isError={false}
      />,
    );

    const modelCalls = screen.getByRole('article', {name: 'Model Calls'});
    expect(modelCalls).toBeInTheDocument();
    expect(within(modelCalls).getByText('3')).toBeInTheDocument();
    expect(within(modelCalls).getByText('of 10 limit')).toBeInTheDocument();

    const tokensUsed = screen.getByRole('article', {name: 'Tokens Used'});
    expect(tokensUsed).toBeInTheDocument();
    expect(within(tokensUsed).getByText('150')).toBeInTheDocument();
    expect(within(tokensUsed).getByText('of 1000 limit')).toBeInTheDocument();
    expect(within(tokensUsed).getByText('Input')).toBeInTheDocument();
    expect(within(tokensUsed).getByText('100')).toBeInTheDocument();
    expect(within(tokensUsed).getByText('Output')).toBeInTheDocument();
    expect(within(tokensUsed).getByText('50')).toBeInTheDocument();

    const toolsCalled = screen.getByRole('article', {name: 'Tools Called'});
    expect(toolsCalled).toBeInTheDocument();
    expect(within(toolsCalled).getByText('2')).toBeInTheDocument();
    expect(within(toolsCalled).getByText('of 5 limit')).toBeInTheDocument();
    expect(
      within(toolsCalled).getByText('Across all model calls in this instance.'),
    ).toBeInTheDocument();
  });

  it('should render model section with provider and model name', () => {
    render(
      <AgentDetails
        agentInstance={mockAgentInstance}
        isLoading={false}
        isError={false}
      />,
    );

    const section = screen.getByTestId('agent-model-section');

    expect(section).toBeInTheDocument();
    expect(within(section).getByText('Provider:')).toBeInTheDocument();
    expect(within(section).getByText('openai')).toBeInTheDocument();
    expect(within(section).getByText('Model:')).toBeInTheDocument();
    expect(within(section).getByText('gpt-4')).toBeInTheDocument();
  });

  it('should render system prompt section with preview text', () => {
    render(
      <AgentDetails
        agentInstance={mockAgentInstance}
        isLoading={false}
        isError={false}
      />,
    );

    const section = screen.getByTestId('agent-system-prompt-section');

    expect(section).toBeInTheDocument();
    expect(
      within(section).getByText('You are a helpful assistant.'),
    ).toBeInTheDocument();
    expect(
      within(section).getByRole('button', {name: 'Copy'}),
    ).toBeInTheDocument();
    expect(
      within(section).getByRole('button', {name: 'Expand'}),
    ).toBeInTheDocument();
  });
});
