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
  type AgentInstanceHistoryItem,
  type QueryAgentInstanceHistoryRequestBody,
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
  it('should keep provider PDF parsing on its source message and show only exported documents on the tool', async () => {
    const document = (
      id: string,
      fileName: string,
      contentType: string,
    ): AgentInstanceHistoryItem['content'][number] => ({
      contentType: 'DOCUMENT',
      documentReference: {
        'camunda.document.type': 'camunda',
        documentId: id,
        storeId: 'default',
        contentHash: 'hash',
        metadata: {
          fileName,
          contentType,
          size: 123,
          expiresAt: null,
          processDefinitionId: null,
          processInstanceKey: null,
          customProperties: {},
        },
      },
    });
    const source = mockAgentInstanceHistoryItem({
      historyItemKey: '1',
      role: 'USER',
      content: [
        document('pdf', 'statement.pdf', 'application/pdf'),
        {
          contentType: 'OBJECT',
          object: {
            evidence: {
              kind: 'documentParsing',
              plugin: 'file-parser',
              pdfEngine: 'native',
              documents: [{documentId: 'pdf', fileName: 'statement.pdf'}],
            },
          },
        },
      ],
      producedAt: '2026-10-19T10:00:00Z',
    });
    const exported = mockAgentInstanceHistoryItem({
      historyItemKey: '2',
      role: 'TOOL_RESULT',
      toolCalls: [
        {
          toolCallId: 'export',
          toolName: 'sandbox_export_document',
          elementId: 'sandbox',
          arguments: {path: '/statement.csv'},
        },
      ],
      content: [document('csv', 'statement.csv', 'text/csv')],
      producedAt: '2026-10-19T10:00:01Z',
    });
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([exported, source]),
    );
    const {user} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );
    const card = within(
      await screen.findByRole('article', {
        name: 'Result for "sandbox_export_document" tool call',
      }),
    );
    expect(
      card.getByRole('listitem', {name: 'statement.csv'}),
    ).toBeInTheDocument();
    expect(
      card.queryByRole('listitem', {name: 'statement.pdf'}),
    ).not.toBeInTheDocument();
    const userMessage = within(screen.getByTestId('conversation-message-1'));
    expect(
      userMessage.getByTestId('evidence-document-parsing'),
    ).toBeInTheDocument();
    expect(
      userMessage.getByRole('listitem', {name: 'statement.pdf'}),
    ).toBeInTheDocument();
    await user.click(card.getByRole('button', {name: 'Expand'}));
    const dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getByRole('heading', {
        name: /^Tool call: sandbox_export_document/,
      }),
    ).toBeInTheDocument();
    expect(
      dialog.queryByText('file-parser (provider-run)'),
    ).not.toBeInTheDocument();
    expect(
      dialog.queryByRole('listitem', {name: 'statement.pdf'}),
    ).not.toBeInTheDocument();
    expect(dialog.getByRole('listitem', {name: 'statement.csv'})).toBeVisible();
    expect(dialog.getByTestId('tool-call-input')).toHaveTextContent(
      '/statement.csv',
    );
    await user.click(dialog.getByRole('button', {name: 'Previous card'}));
    expect(
      within(screen.getByRole('dialog')).getByTestId(
        'evidence-document-parsing',
      ),
    ).toBeVisible();
  });

  it('should place compaction events in history without treating them as transcript cards', async () => {
    const items = [
      mockAgentInstanceHistoryItem({
        historyItemKey: '4',
        role: 'ASSISTANT',
        content: [{contentType: 'TEXT', text: 'After compaction'}],
        producedAt: '2026-10-19T10:00:03Z',
      }),
      mockAgentInstanceHistoryItem({
        historyItemKey: '3',
        role: 'ASSISTANT',
        content: [
          {contentType: 'TEXT', text: 'Turn that compacted context'},
          {
            contentType: 'OBJECT',
            object: {
              evidence: {
                kind: 'compaction',
                savedTokens: 1200,
                message: 'Masked tools A, B, C',
              },
            },
          },
        ],
        producedAt: '2026-10-19T10:00:02Z',
      }),
      mockAgentInstanceHistoryItem({
        historyItemKey: '2',
        role: 'USER',
        content: [{contentType: 'TEXT', text: 'Before compaction'}],
        producedAt: '2026-10-19T10:00:01Z',
      }),
      mockAgentInstanceHistoryItem({
        historyItemKey: '1',
        role: 'CONFIGURATION',
        content: [
          {
            contentType: 'OBJECT',
            object: {evidence: {kind: 'configuration'}},
          },
        ],
        producedAt: '2026-10-19T10:00:00Z',
      }),
    ];
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([...items].reverse()),
    );
    mockSearchAgentInstanceHistory().withSuccess(searchResult(items));
    const {user} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );
    const event = await screen.findByRole('article', {
      name: 'Context compacted',
    });
    expect(event).toHaveTextContent('Saved 1,200 tokens');
    expect(within(event).getByText('Masked tools A, B, C')).toBeVisible();
    expect(screen.getByTestId('conversation-message-4')).toAppearBefore(event);
    expect(event).toAppearBefore(screen.getByTestId('conversation-message-3'));
    expect(screen.getByTestId('conversation-message-3')).toAppearBefore(
      screen.getByTestId('conversation-message-2'),
    );
    await user.click(
      within(screen.getByTestId('conversation-message-4')).getByRole('button', {
        name: 'Expand',
      }),
    );
    expect(within(screen.getByRole('dialog')).getByText('3 / 3')).toBeVisible();
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {name: 'Close'}),
    );
    await user.click(screen.getByRole('button', {name: 'Latest first'}));
    await waitFor(() => {
      const oldestFirstEvent = screen.getByRole('article', {
        name: 'Context compacted',
      });
      expect(screen.getByTestId('conversation-message-2')).toAppearBefore(
        oldestFirstEvent,
      );
      expect(screen.getByTestId('conversation-message-3')).toAppearBefore(
        oldestFirstEvent,
      );
      expect(oldestFirstEvent).toAppearBefore(
        screen.getByTestId('conversation-message-4'),
      );
    });
  });

  it('should walk every card chronologically in one maximized dialog, regardless of list order', async () => {
    const call = {
      toolCallId: 'tc-story',
      toolName: 'sandbox_bash',
      elementId: null,
      arguments: {command: 'echo hello'},
    };
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: '5',
          role: 'ASSISTANT',
          loopIteration: 2,
          content: [{contentType: 'TEXT', text: 'Final answer'}],
        }),
        mockAgentInstanceHistoryItem({
          historyItemKey: '4',
          role: 'TOOL_RESULT',
          loopIteration: 2,
          toolCalls: [call],
          content: [{contentType: 'TEXT', text: 'hello'}],
        }),
        mockAgentInstanceHistoryItem({
          historyItemKey: '3',
          role: 'ASSISTANT',
          content: [],
          toolCalls: [call],
        }),
        mockAgentInstanceHistoryItem({
          historyItemKey: '2',
          role: 'USER',
          content: [{contentType: 'TEXT', text: 'Investigate this claim'}],
        }),
        mockAgentInstanceHistoryItem({
          historyItemKey: '1',
          role: 'CONFIGURATION',
        }),
      ]),
    );
    const {user} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );
    await screen.findByTestId('tool-call-result-tc-story');
    await user.click(
      within(screen.getByTestId('tool-call-result-tc-story')).getByRole(
        'button',
        {name: 'Expand'},
      ),
    );
    let dialog = within(screen.getByRole('dialog'));
    expect(dialog.getByText('3 / 4')).toBeVisible();
    expect(
      dialog.getByRole('navigation', {name: 'History contents'}),
    ).toHaveFocus();
    expect(dialog.getByText('echo hello')).toBeVisible();
    await user.click(dialog.getByRole('button', {name: 'Previous card'}));
    dialog = within(screen.getByRole('dialog'));
    expect(dialog.getByText('2 / 4')).toBeVisible();
    expect(dialog.getAllByText('sandbox_bash').length).toBeGreaterThan(0);
    expect(dialog.getByRole('tab', {name: 'Preview'})).toBeVisible();
    expect(dialog.getByRole('button', {name: 'Next card'})).not.toHaveAttribute(
      'aria-describedby',
    );
    await user.click(dialog.getByRole('button', {name: 'Previous card'}));
    dialog = within(screen.getByRole('dialog'));
    expect(dialog.getByText('Investigate this claim')).toBeVisible();
    expect(dialog.getByRole('button', {name: 'Previous card'})).toBeDisabled();
    for (let i = 0; i < 3; i++) {
      await user.click(
        within(screen.getByRole('dialog')).getByRole('button', {
          name: 'Next card',
        }),
      );
    }
    dialog = within(screen.getByRole('dialog'));
    expect(dialog.getByText('Final answer')).toBeVisible();
    expect(dialog.getByRole('button', {name: 'Next card'})).toBeDisabled();
    await user.keyboard('{ArrowUp}');
    expect(within(screen.getByRole('dialog')).getByText('3 / 4')).toBeVisible();
    expect(
      within(screen.getByRole('dialog')).getByRole('navigation', {
        name: 'History contents',
      }),
    ).toHaveFocus();
    await user.keyboard('{ArrowDown}');
    expect(within(screen.getByRole('dialog')).getByText('4 / 4')).toBeVisible();
    expect(
      within(screen.getByRole('dialog')).getByRole('navigation', {
        name: 'History contents',
      }),
    ).toHaveFocus();
    expect(screen.getAllByRole('dialog')).toHaveLength(1);
    await user.click(
      within(screen.getByRole('dialog')).getByRole('tab', {name: 'Source'}),
    );
    expect(await screen.findByRole('textbox')).toHaveValue('Final answer');
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Previous card',
      }),
    );
    dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getByRole('tab', {name: 'Source', selected: true}),
    ).toBeInTheDocument();
    expect(
      await within(dialog.getByTestId('tool-call-input')).findByRole('textbox'),
    ).toHaveValue(JSON.stringify(call.arguments, null, 2));
    await user.click(dialog.getByRole('button', {name: 'Next card'}));
    dialog = within(screen.getByRole('dialog'));
    expect(
      dialog.getByRole('tab', {name: 'Source', selected: true}),
    ).toBeInTheDocument();
    expect(await dialog.findByRole('textbox')).toHaveValue('Final answer');
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {name: 'Close'}),
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await waitFor(() =>
      expect(
        within(screen.getByTestId('conversation-message-5')).getByRole(
          'button',
          {
            name: 'Expand',
          },
        ),
      ).toHaveFocus(),
    );
  });

  describe('history contents', () => {
    const readCall = (id: string, path: string) => ({
      toolCallId: id,
      toolName: 'read_file',
      elementId: null,
      arguments: {path},
    });
    const grepCall = {
      toolCallId: 'grep-1',
      toolName: 'grep',
      elementId: null,
      arguments: {pattern: 'x'},
    };
    const renderHistory = async () => {
      mockSearchAgentInstanceHistory().withSuccess(
        searchResult([
          mockAgentInstanceHistoryItem({
            historyItemKey: '5',
            role: 'ASSISTANT',
            content: [{contentType: 'TEXT', text: 'Final answer'}],
          }),
          mockAgentInstanceHistoryItem({
            historyItemKey: '4',
            role: 'TOOL_RESULT',
            toolCalls: [grepCall],
            content: [{contentType: 'TEXT', text: 'no matches'}],
          }),
          mockAgentInstanceHistoryItem({
            historyItemKey: '3',
            role: 'TOOL_RESULT',
            toolCalls: [readCall('read-1', '/policies/dispute/policy.md')],
            content: [{contentType: 'TEXT', text: 'policy text'}],
          }),
          mockAgentInstanceHistoryItem({
            historyItemKey: '2',
            role: 'ASSISTANT',
            content: [],
            toolCalls: [
              readCall('read-1', '/policies/dispute/policy.md'),
              grepCall,
            ],
          }),
          mockAgentInstanceHistoryItem({
            historyItemKey: '1',
            role: 'USER',
            content: [{contentType: 'TEXT', text: 'Investigate this claim'}],
          }),
        ]),
      );
      const view = render(
        <ConversationHistory
          agentInstanceKey={AGENT_INSTANCE_KEY}
          availableTools={[]}
          agentInstanceStatus="COMPLETED"
          isVisible
          selectedElementInstanceKey={null}
          agentsElementInstanceKeys={[]}
        />,
        {wrapper: createWrapper()},
      );
      await view.user.click(
        (await screen.findAllByRole('button', {name: 'Expand'}))[0]!,
      );
      return view;
    };
    const contents = () =>
      within(
        within(screen.getByRole('dialog')).getByRole('navigation', {
          name: 'History contents',
        }),
      );

    it('should list deterministic labels in chronological order', async () => {
      await renderHistory();

      const steps = contents().getAllByRole('listitem');
      expect(steps.map((step) => step.textContent)).toEqual([
        '1User message',
        '2Model response 12 requests',
        '3read_filepolicy.md',
        '4grep',
        '5Model response 2',
      ]);
      expect(contents().getByText('Steps · 5')).toBeVisible();
    });

    it('should open a selected step and keep Source view and bounds', async () => {
      const {user} = await renderHistory();

      await user.click(
        within(screen.getByRole('dialog')).getByRole('tab', {name: 'Source'}),
      );
      await user.click(contents().getByRole('button', {name: /policy\.md/}));

      let dialog = within(screen.getByRole('dialog'));
      expect(screen.getAllByRole('dialog')).toHaveLength(1);
      expect(dialog.getByText('Tool call: read_file')).toBeVisible();
      expect(dialog.getByText('3 / 5')).toBeVisible();
      expect(
        dialog.getByRole('tab', {name: 'Source', selected: true}),
      ).toBeInTheDocument();
      expect(
        contents().getByRole('button', {name: /policy\.md/}),
      ).toHaveAttribute('aria-current', 'step');

      await user.click(contents().getByRole('button', {name: /User message/}));
      dialog = within(screen.getByRole('dialog'));
      expect(dialog.getByText('1 / 5')).toBeVisible();
      expect(
        dialog.getByRole('button', {name: 'Previous card'}),
      ).toBeDisabled();
      await user.keyboard('{ArrowUp}');
      expect(dialog.getByText('1 / 5')).toBeVisible();
      await user.keyboard('{ArrowDown}');
      expect(
        within(screen.getByRole('dialog')).getByText('2 / 5'),
      ).toBeVisible();
      expect(
        within(screen.getByRole('dialog')).getByRole('tab', {
          name: 'Source',
          selected: true,
        }),
      ).toBeInTheDocument();
    });

    it('should stop at the last step and ignore Left/Right', async () => {
      const {user} = await renderHistory();

      await user.click(
        contents().getByRole('button', {name: /Model response 2/}),
      );
      await user.keyboard('{ArrowDown}');
      expect(
        within(screen.getByRole('dialog')).getByText('5 / 5'),
      ).toBeVisible();
      await user.keyboard('{ArrowLeft}');
      expect(
        within(screen.getByRole('dialog')).getByText('5 / 5'),
      ).toBeVisible();
    });

    it('should not navigate while the source editor or content has focus', async () => {
      const {user} = await renderHistory();

      await user.click(
        within(screen.getByRole('dialog')).getByRole('tab', {name: 'Source'}),
      );
      await user.click(await screen.findByRole('textbox'));
      const position = within(screen.getByRole('dialog')).getByRole('status');
      const before = position.textContent;
      await user.keyboard('{ArrowUp}');
      await user.keyboard('{ArrowDown}');
      expect(position).toHaveTextContent(before!);
    });
  });

  it('should load earlier cards across a page boundary from the previous arrow', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult(
        [
          mockAgentInstanceHistoryItem({
            historyItemKey: '1',
            role: 'USER',
            content: [{contentType: 'TEXT', text: 'Earlier card'}],
          }),
        ],
        2,
      ),
    );
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult(
        [
          mockAgentInstanceHistoryItem({
            historyItemKey: '2',
            role: 'ASSISTANT',
            content: [{contentType: 'TEXT', text: 'Latest card'}],
          }),
        ],
        2,
      ),
    );
    const {user} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );
    await screen.findByTestId('conversation-message-2');
    await user.click(
      within(screen.getByTestId('conversation-message-2')).getByRole('button', {
        name: 'Expand',
      }),
    );
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Previous card',
      }),
    );
    const dialog = within(screen.getByRole('dialog'));
    expect(await dialog.findByText('Earlier card')).toBeVisible();
    expect(dialog.getByText('1 / 2')).toBeVisible();
    expect(dialog.getByRole('button', {name: 'Previous card'})).toBeDisabled();
    await user.click(dialog.getByRole('button', {name: 'Next card'}));
    expect(
      within(screen.getByRole('dialog')).getByText('Latest card'),
    ).toBeVisible();
  });

  it('should keep the current card on a page-load error and allow retry', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult(
        [
          mockAgentInstanceHistoryItem({
            historyItemKey: '2',
            role: 'ASSISTANT',
            content: [{contentType: 'TEXT', text: 'Latest card'}],
          }),
        ],
        2,
      ),
    );
    const {user} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );
    await screen.findByTestId('conversation-message-2');
    await user.click(
      within(screen.getByTestId('conversation-message-2')).getByRole('button', {
        name: 'Expand',
      }),
    );
    mockSearchAgentInstanceHistory().withServerError();
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Previous card',
      }),
    );
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Could not load more history',
    );
    expect(
      within(screen.getByRole('dialog')).getByText('Latest card'),
    ).toBeVisible();
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult(
        [
          mockAgentInstanceHistoryItem({
            historyItemKey: '1',
            role: 'USER',
            content: [{contentType: 'TEXT', text: 'Earlier card'}],
          }),
        ],
        2,
      ),
    );
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Previous card',
      }),
    );
    expect(
      await within(screen.getByRole('dialog')).findByText('Earlier card'),
    ).toBeVisible();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('should render skeleton while loading', () => {
    mockSearchAgentInstanceHistory().withDelay(searchResult([]));

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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

  it('should load later cards from the next arrow when the list is oldest first', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: '2',
          role: 'ASSISTANT',
          content: [{contentType: 'TEXT', text: 'Latest card'}],
        }),
      ]),
    );
    const {user} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );
    await screen.findByTestId('conversation-message-2');
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult(
        [
          mockAgentInstanceHistoryItem({
            historyItemKey: '1',
            role: 'USER',
            content: [{contentType: 'TEXT', text: 'Earlier card'}],
          }),
        ],
        2,
      ),
    );
    await user.click(screen.getByRole('button', {name: 'Latest first'}));
    await screen.findByTestId('conversation-message-1');
    await user.click(
      within(screen.getByTestId('conversation-message-1')).getByRole('button', {
        name: 'Expand',
      }),
    );
    expect(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Previous card',
      }),
    ).toBeDisabled();
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult(
        [
          mockAgentInstanceHistoryItem({
            historyItemKey: '2',
            role: 'ASSISTANT',
            content: [{contentType: 'TEXT', text: 'Latest card'}],
          }),
        ],
        2,
      ),
    );
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Next card',
      }),
    );
    expect(
      await within(screen.getByRole('dialog')).findByText('Latest card'),
    ).toBeVisible();
    expect(
      within(screen.getByRole('dialog')).getByRole('button', {
        name: 'Next card',
      }),
    ).toBeDisabled();
  });

  it('should render an error message when loading fails', async () => {
    mockSearchAgentInstanceHistory().withServerError(500);

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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
      ]),
    );

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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
  });

  it('should render conversation items with formatted object content', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: '1',
          role: 'ASSISTANT',
          content: [
            {
              contentType: 'OBJECT',
              object: {message: 'Some structured content', hello: 'world'},
            },
          ],
        }),
      ]),
    );

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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
    expect(
      message.getByRole('heading', {name: 'Assistant'}),
    ).toBeInTheDocument();
    expect(message.getByText('Message')).toBeInTheDocument();
    expect(message.getByText('Some structured content')).toBeInTheDocument();
    expect(message.getByText('Hello')).toBeInTheDocument();
    expect(message.getByText('world')).toBeInTheDocument();
    expect(message.queryByText(/[{}"]/)).not.toBeInTheDocument();
  });

  it('should hide inline document tags and render JSON text as fields', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: '1',
          role: 'ASSISTANT',
          content: [
            {
              contentType: 'TEXT',
              text: '<doc id="d-1" fileName="statement.txt" contentType="text/plain" />',
            },
            {
              contentType: 'TEXT',
              text: '{"decision": "APPROVE", "refundAmount": 482.17}',
            },
          ],
        }),
      ]),
    );

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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
    expect(message.getByText('Decision')).toBeInTheDocument();
    expect(message.getByText('APPROVE')).toBeInTheDocument();
    expect(message.getByText('Refund amount')).toBeInTheDocument();
    expect(message.getByText('482.17')).toBeInTheDocument();
    expect(message.queryByText(/<doc|[{}]/)).not.toBeInTheDocument();
  });

  it('should render tool results in the history', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: '1',
          role: 'TOOL_RESULT',
          content: [{contentType: 'TEXT', text: 'Tool output here'}],
          toolCalls: [
            {
              toolCallId: 'tc-1',
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
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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
      screen.getByRole('article', {name: 'Result for "search" tool call'}),
    );
    expect(
      toolResultMessage.getByRole('heading', {name: 'search'}),
    ).toBeInTheDocument();
    expect(toolResultMessage.getByText('Tool output here')).toBeInTheDocument();
  });

  it('should trigger a refetch when the agent instance status changes', async () => {
    mockSearchAgentInstanceHistory(AGENT_INSTANCE_KEY).withSuccess(
      searchResult([mockAgentInstanceHistoryItem({historyItemKey: 'msg-2'})]),
    );
    mockSearchAgentInstanceHistory(AGENT_INSTANCE_KEY).withSuccess(
      searchResult([mockAgentInstanceHistoryItem({historyItemKey: 'msg-1'})]),
    );

    const {rerender} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="IDLE"
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );

    await waitFor(() =>
      expect(screen.getByTestId('conversation-message-msg-1')).toBeVisible(),
    );

    rerender(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="TOOL_CALLING"
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
    );

    await waitFor(() =>
      expect(screen.getByTestId('conversation-message-msg-2')).toBeVisible(),
    );
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
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    const sortButton = screen.getByRole('button', {name: 'Latest first'});
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
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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

    const {user} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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
      message.getByRole('listitem', {name: 'report.txt'}),
    ).toBeInTheDocument();
    expect(
      message.getByRole('listitem', {name: 'screenshot.png'}),
    ).toBeInTheDocument();
    await user.click(message.getByRole('button', {name: 'Expand'}));
    const expanded = within(screen.getByRole('dialog'));
    expect(
      expanded.getByRole('listitem', {name: 'screenshot.png'}),
    ).toBeInTheDocument();
    await user.click(
      expanded.getByRole('button', {
        name: 'Preview document for screenshot.png',
      }),
    );
    expect(
      screen.getByRole('heading', {name: 'Preview: screenshot.png'}),
    ).toBeInTheDocument();
  });

  it('should render tool calls', async () => {
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
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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
      assistantMessage.getByRole('listitem', {name: 'greet'}),
    ).toBeInTheDocument();
    expect(
      assistantMessage.getByRole('listitem', {name: 'search'}),
    ).toBeInTheDocument();
  });

  it('should render metrics when they are available for a message', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: '1',
          role: 'ASSISTANT',
          content: [{contentType: 'TEXT', text: 'Here is my answer.'}],
          metrics: {
            inputTokens: 100,
            outputTokens: 50,
            cacheCreationTokenCount: 80,
            cacheReadTokenCount: 60,
            reasoningTokenCount: 20,
            durationMs: 1234,
          },
        }),
        mockAgentInstanceHistoryItem({
          historyItemKey: '2',
          role: 'ASSISTANT',
          content: [{contentType: 'TEXT', text: 'Here is my answer.'}],
          metrics: null,
        }),
      ]),
    );

    const {user} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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
      '290 tokens',
    );
    expect(message.getByTestId('message-duration-metric')).toHaveTextContent(
      '1.23s',
    );

    await user.hover(message.getByTestId('message-token-metric'));
    const tooltip = await screen.findByRole('tooltip');
    expect(tooltip).toHaveTextContent('Input: 100');
    expect(tooltip).toHaveTextContent('Cache read: 60');
    expect(tooltip).toHaveTextContent('Output: 50 (20 reasoning)');
    expect(tooltip).toHaveTextContent('Cache write: 80');

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
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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
          const req =
            (await request.json()) as QueryAgentInstanceHistoryRequestBody;
          filter = req.filter;
          return Response.json(searchResult([item]));
        },
      ),
    );

    const {user} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
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

  it('should insert iteration markers after messages in most-recent sorting order', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: '3',
          loopIteration: 2,
          role: 'ASSISTANT',
          content: [{contentType: 'TEXT', text: 'message 3'}],
        }),
        mockAgentInstanceHistoryItem({
          historyItemKey: '2',
          loopIteration: 2,
          role: 'USER',
          content: [{contentType: 'TEXT', text: 'message 2'}],
        }),
        mockAgentInstanceHistoryItem({
          historyItemKey: '1',
          loopIteration: 1,
          role: 'ASSISTANT',
          content: [{contentType: 'TEXT', text: 'message 1'}],
        }),
      ]),
    );

    render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    const firstMarker = screen.getByText('1. iteration');
    expect(firstMarker).toBeVisible();
    const firstMessage = screen.getByTestId('conversation-message-1');
    expect(
      firstMarker.compareDocumentPosition(firstMessage) &
        Node.DOCUMENT_POSITION_PRECEDING,
    ).toBeTruthy();

    const secondMarker = screen.getByText('2. iteration');
    expect(secondMarker).toBeVisible();
    const secondMessage = screen.getByTestId('conversation-message-2');
    const thirdMessage = screen.getByTestId('conversation-message-3');
    expect(
      secondMarker.compareDocumentPosition(secondMessage) &
        Node.DOCUMENT_POSITION_PRECEDING,
    ).toBeTruthy();
    expect(
      secondMarker.compareDocumentPosition(thirdMessage) &
        Node.DOCUMENT_POSITION_PRECEDING,
    ).toBeTruthy();
  });

  it('should insert iteration markers before messages in oldest first sorting order', async () => {
    mockSearchAgentInstanceHistory().withSuccess(
      searchResult([
        mockAgentInstanceHistoryItem({
          historyItemKey: '1',
          loopIteration: 1,
          role: 'ASSISTANT',
          content: [{contentType: 'TEXT', text: 'message 1'}],
        }),
        mockAgentInstanceHistoryItem({
          historyItemKey: '2',
          loopIteration: 2,
          role: 'USER',
          content: [{contentType: 'TEXT', text: 'message 2'}],
        }),
        mockAgentInstanceHistoryItem({
          historyItemKey: '3',
          loopIteration: 2,
          role: 'ASSISTANT',
          content: [{contentType: 'TEXT', text: 'message 3'}],
        }),
      ]),
    );
    // Mock data for initial "latest first" sorting
    mockSearchAgentInstanceHistory().withSuccess(searchResult([]));

    const {user} = render(
      <ConversationHistory
        agentInstanceKey={AGENT_INSTANCE_KEY}
        availableTools={[]}
        agentInstanceStatus="COMPLETED"
        isVisible
        selectedElementInstanceKey={null}
        agentsElementInstanceKeys={[]}
      />,
      {wrapper: createWrapper()},
    );

    await waitForElementToBeRemoved(
      screen.queryByTestId('conversation-history-skeleton'),
    );

    await user.click(screen.getByRole('button', {name: 'Latest first'}));
    expect(screen.getByRole('button', {name: 'Oldest first'})).toBeVisible();

    const firstMarker = await screen.findByText('1. iteration');
    expect(firstMarker).toBeVisible();
    const firstMessage = screen.getByTestId('conversation-message-1');
    expect(
      firstMarker.compareDocumentPosition(firstMessage) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();

    const secondMarker = screen.getByText('2. iteration');
    expect(secondMarker).toBeVisible();
    const secondMessage = screen.getByTestId('conversation-message-2');
    const thirdMessage = screen.getByTestId('conversation-message-3');
    expect(
      secondMarker.compareDocumentPosition(secondMessage) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(
      secondMarker.compareDocumentPosition(thirdMessage) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });
});
