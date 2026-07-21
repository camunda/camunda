/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import type {ReactNode} from 'react';
import {mockSearchAgentInstanceHistory} from 'modules/mocks/api/v2/agentInstances/searchAgentInstanceHistory';
import {LatestAgentMessage} from './LatestAgentMessage';
import {searchResult} from 'modules/testUtils';
import {mockAgentInstanceHistoryItem} from 'modules/mocks/mockAgentInstanceHistoryItem';

const AGENT_INSTANCE_KEY = '2251799813851828';

function createWrapper() {
  const queryClient = getMockQueryClient();
  return ({children}: {children: ReactNode}) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
        <Routes>
          <Route path={Paths.processInstance()} element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('<LatestAgentMessage />', () => {
  it('should render skeleton while loading', () => {
    mockSearchAgentInstanceHistory(AGENT_INSTANCE_KEY).withDelay(
      searchResult([]),
    );

    render(
      <LatestAgentMessage
        agentInstanceKey={AGENT_INSTANCE_KEY}
        agentInstanceStatus="COMPLETED"
      />,
      {wrapper: createWrapper()},
    );

    expect(
      screen.getByTestId('latest-agent-message-skeleton'),
    ).toBeInTheDocument();
  });

  it('should render a hint when no agent message exists yet', async () => {
    mockSearchAgentInstanceHistory(AGENT_INSTANCE_KEY).withSuccess(
      searchResult([]),
    );

    render(
      <LatestAgentMessage
        agentInstanceKey={AGENT_INSTANCE_KEY}
        agentInstanceStatus="COMPLETED"
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('latest-agent-message-skeleton'),
    );

    expect(
      screen.getByText('The agent has not produced a message yet.'),
    ).toBeInTheDocument();
  });

  it('should render an error hint when loading fails', async () => {
    mockSearchAgentInstanceHistory(AGENT_INSTANCE_KEY).withServerError(500);

    render(
      <LatestAgentMessage
        agentInstanceKey={AGENT_INSTANCE_KEY}
        agentInstanceStatus="COMPLETED"
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('latest-agent-message-skeleton'),
    );

    expect(
      screen.getByText('Failed to load latest agent message.'),
    ).toBeInTheDocument();
  });

  it('should render the latest assistant message', async () => {
    mockSearchAgentInstanceHistory(AGENT_INSTANCE_KEY).withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: 'msg-1',
          role: 'ASSISTANT',
          content: [{contentType: 'TEXT', text: 'I will help you with that.'}],
        }),
      ]),
    );

    render(
      <LatestAgentMessage
        agentInstanceKey={AGENT_INSTANCE_KEY}
        agentInstanceStatus="COMPLETED"
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('latest-agent-message-skeleton'),
    );

    const message = within(screen.getByTestId('conversation-message-msg-1'));
    expect(
      message.getByRole('heading', {name: 'Assistant'}),
    ).toBeInTheDocument();
    expect(message.getByText('I will help you with that.')).toBeInTheDocument();
  });

  it('should trigger a refetch when the agent instance status changes', async () => {
    mockSearchAgentInstanceHistory(AGENT_INSTANCE_KEY).withSuccess(
      searchResult([mockAgentInstanceHistoryItem({historyItemKey: 'msg-2'})]),
    );
    mockSearchAgentInstanceHistory(AGENT_INSTANCE_KEY).withSuccess(
      searchResult([mockAgentInstanceHistoryItem({historyItemKey: 'msg-1'})]),
    );

    const {rerender} = render(
      <LatestAgentMessage
        agentInstanceKey={AGENT_INSTANCE_KEY}
        agentInstanceStatus="IDLE"
      />,
      {wrapper: createWrapper()},
    );

    await waitFor(() =>
      expect(screen.getByTestId('conversation-message-msg-1')).toBeVisible(),
    );

    rerender(
      <LatestAgentMessage
        agentInstanceKey={AGENT_INSTANCE_KEY}
        agentInstanceStatus="TOOL_CALLING"
      />,
    );

    await waitFor(() =>
      expect(screen.getByTestId('conversation-message-msg-2')).toBeVisible(),
    );
  });

  it('should render tool call intents for a message with tool calls', async () => {
    mockSearchAgentInstanceHistory(AGENT_INSTANCE_KEY).withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: 'msg-2',
          role: 'ASSISTANT',
          content: [{contentType: 'TEXT', text: 'Calling a tool.'}],
          toolCalls: [
            {
              toolCallId: 'tc-1',
              toolName: 'doSomething',
              elementId: 'element-1',
              arguments: {},
            },
            {
              toolCallId: 'tc-2',
              toolName: 'noElement',
              elementId: null,
              arguments: {},
            },
          ],
        }),
      ]),
    );

    render(
      <LatestAgentMessage
        agentInstanceKey={AGENT_INSTANCE_KEY}
        agentInstanceStatus="COMPLETED"
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('latest-agent-message-skeleton'),
    );

    const message = within(screen.getByTestId('conversation-message-msg-2'));
    expect(
      message.getByRole('button', {name: '"doSomething" tool call.'}),
    ).toBeDisabled();
    expect(
      message.getByRole('button', {name: '"noElement" tool call.'}),
    ).toBeDisabled();
  });
});
