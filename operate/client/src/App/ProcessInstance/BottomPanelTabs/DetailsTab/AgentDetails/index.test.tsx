/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  act,
  render,
  screen,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import type {ReactNode} from 'react';
import {mockSearchAgentInstanceHistory} from 'modules/mocks/api/v2/agentInstances/searchAgentInstanceHistory';
import {searchResult} from 'modules/testUtils';
import {Paths} from 'modules/Routes';
import {AgentDetails} from './index';
import {mockAgentInstanceHistoryItem} from 'modules/mocks/mockAgentInstanceHistoryItem';
import {mockAgentInstance} from 'modules/mocks/mockAgentInstance';

function createWrapper() {
  const queryClient = getMockQueryClient();
  return ({children}: {children: ReactNode}) => {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
          <Routes>
            <Route path={Paths.processInstance()} element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };
}

const agentInstance = mockAgentInstance();

describe('<AgentDetails />', () => {
  beforeEach(() => {
    mockSearchAgentInstanceHistory().withSuccess(searchResult([]));
  });

  it('should render AI Agent heading and status for TOOL_CALLING', () => {
    render(
      <AgentDetails
        agentInstance={agentInstance}
        isLoading={false}
        isError={false}
        selectedElementInstanceKey={null}
      />,
      {wrapper: createWrapper()},
    );

    expect(screen.getByText('AI Agent')).toBeInTheDocument();
    const statusSection = screen.getByTestId('agent-status-section');
    expect(
      within(statusSection).getByText('Status: Calling tools'),
    ).toBeInTheDocument();
  });

  it('should render status for THINKING', () => {
    render(
      <AgentDetails
        agentInstance={{...agentInstance, status: 'THINKING'}}
        isLoading={false}
        isError={false}
        selectedElementInstanceKey={null}
      />,
      {wrapper: createWrapper()},
    );

    const statusSection = screen.getByTestId('agent-status-section');
    expect(
      within(statusSection).getByText('Status: Thinking'),
    ).toBeInTheDocument();
  });

  it('should render status for IDLE', () => {
    render(
      <AgentDetails
        agentInstance={{...agentInstance, status: 'IDLE'}}
        isLoading={false}
        isError={false}
        selectedElementInstanceKey={null}
      />,
      {wrapper: createWrapper()},
    );

    const statusSection = screen.getByTestId('agent-status-section');
    expect(within(statusSection).getByText('Status: Idle')).toBeInTheDocument();
  });

  it('should render status for COMPLETED', () => {
    render(
      <AgentDetails
        agentInstance={{...agentInstance, status: 'COMPLETED'}}
        isLoading={false}
        isError={false}
        selectedElementInstanceKey={null}
      />,
      {wrapper: createWrapper()},
    );

    const statusSection = screen.getByTestId('agent-status-section');
    expect(
      within(statusSection).getByText('Status: Completed'),
    ).toBeInTheDocument();
  });

  it('should render status for INITIALIZING', () => {
    render(
      <AgentDetails
        agentInstance={{...agentInstance, status: 'INITIALIZING'}}
        isLoading={false}
        isError={false}
        selectedElementInstanceKey={null}
      />,
      {wrapper: createWrapper()},
    );

    const statusSection = screen.getByTestId('agent-status-section');
    expect(
      within(statusSection).getByText('Status: Initializing'),
    ).toBeInTheDocument();
  });

  it('should render status for TOOL_DISCOVERY', () => {
    render(
      <AgentDetails
        agentInstance={{...agentInstance, status: 'TOOL_DISCOVERY'}}
        isLoading={false}
        isError={false}
        selectedElementInstanceKey={null}
      />,
      {wrapper: createWrapper()},
    );

    const statusSection = screen.getByTestId('agent-status-section');
    expect(
      within(statusSection).getByText('Status: Discovering tools'),
    ).toBeInTheDocument();
  });

  it('should render loading state', () => {
    render(
      <AgentDetails
        agentInstance={undefined}
        isLoading={true}
        isError={false}
        selectedElementInstanceKey={null}
      />,
    );

    expect(screen.getByText('AI Agent')).toBeInTheDocument();
    expect(screen.getByTestId('agent-details-skeleton')).toBeInTheDocument();
  });

  it('should render error state when fetch fails', () => {
    render(
      <AgentDetails
        agentInstance={undefined}
        isLoading={false}
        isError={true}
        selectedElementInstanceKey={null}
      />,
    );

    expect(screen.getByText('AI Agent')).toBeInTheDocument();
    expect(
      screen.getByText('Unable to load agent information.'),
    ).toBeInTheDocument();
  });

  it('should open the status accordion item by default and display the latest agent message', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([mockAgentInstanceHistoryItem({role: 'ASSISTANT'})]),
    );

    render(
      <AgentDetails
        agentInstance={agentInstance}
        isLoading={false}
        isError={false}
        selectedElementInstanceKey={null}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('latest-agent-message-skeleton'),
    );

    const statusSection = within(screen.getByTestId('agent-status-section'));
    expect(
      statusSection.getByRole('button', {
        name: 'Status: Calling tools',
        expanded: true,
      }),
    ).toBeVisible();
    expect(
      statusSection.getByRole('article', {name: 'Assistant message'}),
    ).toBeVisible();
  });

  it('should render usage metrics', () => {
    render(
      <AgentDetails
        agentInstance={agentInstance}
        isLoading={false}
        isError={false}
        selectedElementInstanceKey={null}
      />,
      {wrapper: createWrapper()},
    );

    const section = within(screen.getByTestId('agent-usage-section'));

    const sectionHeader = section.getByRole('button', {name: 'Usage'});
    expect(sectionHeader).toHaveTextContent('3 model calls');
    expect(sectionHeader).toHaveTextContent('150 tokens');

    const modelCalls = section.getByRole('article', {name: 'Model Calls'});
    expect(modelCalls).toBeInTheDocument();
    expect(within(modelCalls).getByText('3')).toBeInTheDocument();
    expect(within(modelCalls).getByText('of 10 limit')).toBeInTheDocument();

    const tokensUsed = section.getByRole('article', {name: 'Tokens Used'});
    expect(tokensUsed).toBeInTheDocument();
    expect(within(tokensUsed).getByText('150')).toBeInTheDocument();
    expect(
      within(tokensUsed).getByText(`of ${(1000).toLocaleString()} limit`),
    ).toBeInTheDocument();
    expect(within(tokensUsed).getByText('Input')).toBeInTheDocument();
    expect(within(tokensUsed).getByText('100')).toBeInTheDocument();
    expect(within(tokensUsed).getByText('Output')).toBeInTheDocument();
    expect(within(tokensUsed).getByText('50')).toBeInTheDocument();

    const toolsCalled = section.getByRole('article', {name: 'Tools Called'});
    expect(toolsCalled).toBeInTheDocument();
    expect(within(toolsCalled).getByText('2')).toBeInTheDocument();
    expect(within(toolsCalled).getByText('of 5 limit')).toBeInTheDocument();
    expect(
      within(toolsCalled).getByText('Across all model calls in this instance.'),
    ).toBeInTheDocument();
  });

  it('should render the model provider and name', () => {
    render(
      <AgentDetails
        agentInstance={agentInstance}
        isLoading={false}
        isError={false}
        selectedElementInstanceKey={null}
      />,
      {wrapper: createWrapper()},
    );

    const section = screen.getByTestId('agent-model-section');

    expect(section).toBeInTheDocument();
    expect(within(section).getByText('Provider:')).toBeInTheDocument();
    expect(within(section).getByText('openai')).toBeInTheDocument();
    expect(within(section).getByText('Model:')).toBeInTheDocument();
    expect(within(section).getByText('gpt-4')).toBeInTheDocument();
  });

  it('should render tools available for the agent instance', () => {
    render(
      <AgentDetails
        agentInstance={mockAgentInstance({
          tools: [
            {name: 'get_weather', description: null, elementId: null},
            {name: 'tell_joke', description: null, elementId: null},
          ],
        })}
        isLoading={false}
        isError={false}
        selectedElementInstanceKey={null}
      />,
      {wrapper: createWrapper()},
    );

    const section = screen.getByTestId('agent-available-tools-section');
    expect(section).toBeInTheDocument();
    expect(
      within(section).getByRole('listitem', {name: 'get_weather'}),
    ).toBeInTheDocument();
    expect(
      within(section).getByRole('listitem', {name: 'tell_joke'}),
    ).toBeInTheDocument();
  });

  it('should render the system prompt with copy and expand options', () => {
    render(
      <AgentDetails
        agentInstance={agentInstance}
        isLoading={false}
        isError={false}
        selectedElementInstanceKey={null}
      />,
      {wrapper: createWrapper()},
    );

    const section = screen.getByTestId('agent-system-prompt-section');

    expect(section).toBeInTheDocument();
    expect(
      within(section).getByText('You are a helpful assistant.'),
    ).toBeInTheDocument();
    expect(
      within(section).getByRole('button', {name: 'Copy to clipboard'}),
    ).toBeInTheDocument();
    expect(
      within(section).getByRole('button', {name: 'Expand'}),
    ).toBeInTheDocument();
  });

  it('should not fetch the conversation history until its accordion item is opened', async () => {
    const historySpy = vi.fn();
    mockSearchAgentInstanceHistory(agentInstance.agentInstanceKey).withSuccess(
      searchResult([]),
      {mockResolverFn: historySpy},
    );
    // Latest message is always fetched. Handle first before history spy handler.
    mockSearchAgentInstanceHistory().withSuccess(searchResult([]));

    const {user} = render(
      <AgentDetails
        agentInstance={agentInstance}
        isLoading={false}
        isError={false}
        selectedElementInstanceKey={null}
      />,
      {wrapper: createWrapper()},
    );

    const section = screen.getByTestId('agent-conversation-history-section');
    expect(section).toBeInTheDocument();

    // Flush potentially pending queries otherwise this test cannot break.
    // eslint-disable-next-line testing-library/no-unnecessary-act
    await act(async () => {});
    expect(historySpy).not.toHaveBeenCalled();

    await user.click(
      within(section).getByRole('button', {name: 'Conversation history'}),
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    expect(historySpy).toHaveBeenCalledTimes(1);
  });
});
