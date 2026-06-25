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
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import type {ReactNode} from 'react';
import {mockSearchAgentInstanceHistory} from 'modules/mocks/api/v2/agentInstances/searchAgentInstanceHistory';
import {ConversationHistory} from './index';
import {searchResult} from 'modules/testUtils';
import {mockAgentInstanceHistoryItem} from 'modules/mocks/mockAgentInstanceHistoryItem';
import {Paths} from 'modules/Routes';
import {mockServer} from 'modules/mock-server/node';
import {http} from 'msw';
import {
  endpoints,
  type QueryAgentInstancesRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.10';

const AGENT_INSTANCE_KEY = '2251799813851828';

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

describe('<ConversationHistory />', () => {
  it('should render skeleton while loading', () => {
    mockSearchAgentInstanceHistory().withDelay(searchResult([]));

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        enablePeriodicRefetch={false}
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );

    expect(
      screen.getByTestId('conversation-history-skeleton'),
    ).toBeInTheDocument();
  });

  it('should render an error message when loading fails', async () => {
    mockSearchAgentInstanceHistory().withServerError(500);

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        enablePeriodicRefetch={false}
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    expect(
      screen.getByText('Failed to load conversation history.'),
    ).toBeInTheDocument();
  });

  it('should show a hint when there are no conversation messages', async () => {
    mockSearchAgentInstanceHistory().withSuccess(searchResult([]));

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        enablePeriodicRefetch={false}
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    expect(
      screen.getByText('No conversation with this agent instance found.'),
    ).toBeInTheDocument();
  });

  it('should render conversation items with text', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: '1',
          role: 'USER',
          content: [{contentType: 'TEXT', text: 'Hello, agent!'}],
        }),
        mockAgentInstanceHistoryItem({
          historyItemKey: '2',
          role: 'ASSISTANT',
          content: [{contentType: 'TEXT', text: 'Hello, user!'}],
        }),
        mockAgentInstanceHistoryItem({
          historyItemKey: '3',
          role: 'TOOL_RESULT',
          content: [{contentType: 'TEXT', text: 'Tool output here'}],
        }),
      ]),
    );

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        enablePeriodicRefetch={false}
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    const userMessage = within(screen.getByTestId('conversation-message-1'));
    expect(
      userMessage.getByRole('heading', {name: 'User'}),
    ).toBeInTheDocument();
    expect(userMessage.getByText('Hello, agent!')).toBeInTheDocument();

    const agentMessage = within(screen.getByTestId('conversation-message-2'));
    expect(
      agentMessage.getByRole('heading', {name: 'Assistant'}),
    ).toBeInTheDocument();
    expect(agentMessage.getByText('Hello, user!')).toBeInTheDocument();

    const toolResultMessage = within(
      screen.getByTestId('conversation-message-3'),
    );
    expect(
      toolResultMessage.getByRole('heading', {name: 'Tool Result'}),
    ).toBeInTheDocument();
    expect(screen.getByText('Tool output here')).toBeInTheDocument();
  });

  it('should render conversation items with formatted object content', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: '1',
          role: 'TOOL_RESULT',
          content: [
            {
              contentType: 'OBJECT',
              object: {message: 'Tool output here', hello: 'world'},
            },
          ],
        }),
      ]),
    );

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        enablePeriodicRefetch={false}
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    const toolResultMessage = within(
      screen.getByTestId('conversation-message-1'),
    );
    expect(
      toolResultMessage.getByRole('heading', {name: 'Tool Result'}),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        '{\n  "message": "Tool output here",\n  "hello": "world"\n}',
        {
          normalizer: (text) => text,
        },
      ),
    ).toBeInTheDocument();
  });

  it('should toggle the history sort order when the sort button is clicked', async () => {
    let query: unknown;
    const item = mockAgentInstanceHistoryItem();
    mockServer.use(
      http.post(
        endpoints.queryAgentInstanceHistory.getUrl({
          agentInstanceKey: AGENT_INSTANCE_KEY,
        }),
        async ({request}) => {
          query = await request.json();
          return Response.json(searchResult([item]));
        },
      ),
    );

    const {user} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        enablePeriodicRefetch={false}
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    const sortButton = screen.getByRole('button', {name: 'Most recent first'});
    expect(sortButton).toBeVisible();
    expect(query).toEqual(
      expect.objectContaining({
        sort: [{field: 'producedAt', order: 'desc'}],
      }),
    );

    await user.click(sortButton);

    expect(screen.getByRole('button', {name: 'Oldest first'})).toBeVisible();
    await waitFor(() =>
      expect(query).toEqual(
        expect.objectContaining({
          sort: [{field: 'producedAt', order: 'asc'}],
        }),
      ),
    );
  });

  it('should show a "Show more" button when more items exist and load them on click', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult(
        [
          mockAgentInstanceHistoryItem({
            historyItemKey: '2',
            role: 'ASSISTANT',
            content: [{contentType: 'TEXT', text: 'Second page message'}],
          }),
        ],
        2,
      ),
    );
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult(
        [
          mockAgentInstanceHistoryItem({
            historyItemKey: '1',
            role: 'USER',
            content: [{contentType: 'TEXT', text: 'First page message'}],
          }),
        ],
        2,
      ),
    );

    const {user} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        enablePeriodicRefetch={false}
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    expect(screen.getByText('First page message')).toBeInTheDocument();
    expect(screen.queryByText('Second page message')).not.toBeInTheDocument();

    const showMoreButton = screen.getByRole('button', {name: 'Show more'});
    expect(showMoreButton).toBeInTheDocument();

    await user.click(showMoreButton);

    expect(await screen.findByText('Second page message')).toBeInTheDocument();
    expect(screen.getByText('First page message')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Show more'}),
    ).not.toBeInTheDocument();
  });

  it('should render document references', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: '1',
          role: 'USER',
          content: [
            {contentType: 'TEXT', text: 'Here are the documents.'},
            {
              contentType: 'DOCUMENT',
              documentReference: {
                'camunda.document.type': 'camunda',
                contentHash: 'abc123',
                documentId: 'doc-1',
                storeId: 'default',
                metadata: {
                  contentType: 'text/plain',
                  fileName: 'report.txt',
                  size: 1234,
                  expiresAt: null,
                  processDefinitionId: null,
                  processInstanceKey: null,
                  customProperties: {},
                },
              },
            },
            {
              contentType: 'DOCUMENT',
              documentReference: {
                'camunda.document.type': 'camunda',
                contentHash: 'def456',
                documentId: 'doc-2',
                storeId: 'default',
                metadata: {
                  contentType: 'image/png',
                  fileName: 'screenshot.png',
                  size: 5678,
                  expiresAt: null,
                  processDefinitionId: null,
                  processInstanceKey: null,
                  customProperties: {},
                },
              },
            },
          ],
        }),
      ]),
    );

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        enablePeriodicRefetch={false}
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    const message = within(screen.getByTestId('conversation-message-1'));
    expect(message.getByText('Here are the documents.')).toBeInTheDocument();
    expect(
      message.getByRole('button', {name: 'report.txt'}),
    ).toBeInTheDocument();
    expect(
      message.getByRole('button', {name: 'screenshot.png'}),
    ).toBeInTheDocument();
  });

  it('should render tool calls and enable them when a tool links to an element', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: '1',
          role: 'ASSISTANT',
          content: [{contentType: 'TEXT', text: 'Calling tools now.'}],
          toolCalls: [
            {
              toolCallId: 'tc-1',
              toolName: 'greet',
              elementId: 'greet-element',
              arguments: {},
            },
            {
              toolCallId: 'tc-2',
              toolName: 'search',
              elementId: null,
              arguments: {},
            },
          ],
        }),
      ]),
    );

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        enablePeriodicRefetch={false}
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    const assistantMessage = within(
      screen.getByTestId('conversation-message-1'),
    );
    expect(
      assistantMessage.getByRole('button', {
        name: '"greet" tool call. Click to open details.',
      }),
    ).toBeEnabled();
    expect(
      assistantMessage.getByRole('button', {name: '"search" tool call.'}),
    ).toBeDisabled();
  });

  it('should render metrics when they are available for a message', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: '1',
          role: 'ASSISTANT',
          content: [{contentType: 'TEXT', text: 'Here is my answer.'}],
          metrics: {inputTokens: 100, outputTokens: 50, durationMs: 1234},
        }),
        mockAgentInstanceHistoryItem({
          historyItemKey: '2',
          role: 'ASSISTANT',
          content: [{contentType: 'TEXT', text: 'Here is my answer.'}],
          metrics: null,
        }),
      ]),
    );

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        enablePeriodicRefetch={false}
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    const message = within(screen.getByTestId('conversation-message-1'));
    expect(message.getByTestId('message-token-metric')).toHaveTextContent(
      '150 tokens',
    );
    expect(message.getByTestId('message-duration-metric')).toHaveTextContent(
      '1.23s',
    );
    expect(message.getByText('Input: 100 · Output: 50')).toBeInTheDocument();

    const messageNoMetrics = within(
      screen.getByTestId('conversation-message-2'),
    );
    expect(
      messageNoMetrics.queryByTestId('message-token-metric'),
    ).not.toBeInTheDocument();
    expect(
      messageNoMetrics.queryByTestId('message-duration-metric'),
    ).not.toBeInTheDocument();
  });

  it('should only show a scope toggle when the agent was activated multiple times', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([mockAgentInstanceHistoryItem({role: 'ASSISTANT'})]),
    );
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([mockAgentInstanceHistoryItem({role: 'ASSISTANT'})]),
    );

    const {rerender} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        enablePeriodicRefetch={false}
        isVisible
        selectedElementInstanceKey="111"
        agentsElementInstanceKeys={['111']}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    expect(
      screen.queryByRole('button', {name: 'Scoped conversation'}),
    ).not.toBeInTheDocument();

    rerender(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        enablePeriodicRefetch={false}
        isVisible
        selectedElementInstanceKey="111"
        agentsElementInstanceKeys={['111', '222']}
      />,
    );

    expect(
      screen.getByRole('button', {name: 'Scoped conversation'}),
    ).toBeVisible();
  });

  it('should show a scoped empty hint when the agent was activated multiple times', async () => {
    mockSearchAgentInstanceHistory().withSuccess(searchResult([]));

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        enablePeriodicRefetch={false}
        isVisible
        selectedElementInstanceKey="111"
        agentsElementInstanceKeys={['111', '222']}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    expect(
      screen.getByText('No scoped conversation with the agent instance found.'),
    ).toBeVisible();
  });

  it('should toggle between scoped and whole conversation history', async () => {
    let filter: unknown;
    const item = mockAgentInstanceHistoryItem();
    mockServer.use(
      http.post(
        endpoints.queryAgentInstanceHistory.getUrl({
          agentInstanceKey: AGENT_INSTANCE_KEY,
        }),
        async ({request}) => {
          const req = (await request.json()) as QueryAgentInstancesRequestBody;
          filter = req.filter;
          return Response.json(searchResult([item]));
        },
      ),
    );

    const {user} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        enablePeriodicRefetch={false}
        isVisible
        selectedElementInstanceKey="111"
        agentsElementInstanceKeys={['111', '222']}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    const scopeButton = screen.getByRole('button', {
      name: 'Scoped conversation',
    });
    expect(scopeButton).toBeVisible();
    expect(filter).toEqual(
      expect.objectContaining({elementInstanceKey: '111'}),
    );

    await user.click(scopeButton);

    expect(
      screen.getByRole('button', {name: 'Whole conversation'}),
    ).toBeVisible();
    await waitFor(() =>
      expect(filter).not.toEqual(
        expect.objectContaining({elementInstanceKey: '111'}),
      ),
    );
  });
});
