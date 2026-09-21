/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, within} from 'modules/testing-library';
import type {AgentInstanceHistoryItem} from '@camunda/camunda-api-zod-schemas/8.10';
import {ConversationMessage} from './index';

vi.unmock('react-markdown');

type ContentItem = AgentInstanceHistoryItem['content'][number];

function createDocumentContent(): ContentItem {
  return {
    contentType: 'DOCUMENT',
    documentReference: {
      'camunda.document.type': 'camunda',
      contentHash: 'hash',
      documentId: 'document-id',
      storeId: 'default',
      metadata: {
        contentType: 'text/plain',
        fileName: 'evidence.txt',
        size: 100,
        expiresAt: null,
        processDefinitionId: null,
        processInstanceKey: null,
        customProperties: {},
      },
    },
  };
}

describe('<ConversationMessage />', () => {
  it('should open a markdown preview modal when expand is clicked', async () => {
    const {user} = render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[{contentType: 'TEXT', text: 'Hello **world**'}]}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Expand'}));

    const modal = screen.getByRole('dialog');
    expect(modal).toBeInTheDocument();
    expect(within(modal).getByText('Assistant message')).toBeInTheDocument();
    expect(within(modal).getByText('world')).toBeInTheDocument();
  });

  it('should close the modal when close button is clicked', async () => {
    const {user} = render(
      <ConversationMessage
        actor="SYSTEM"
        content={[{contentType: 'TEXT', text: 'System message'}]}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Expand'}));
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Close'}));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('should show correct modal title for each actor', async () => {
    const {user, rerender} = render(
      <ConversationMessage
        actor="SYSTEM"
        content={[{contentType: 'TEXT', text: 'prompt'}]}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Expand'}));
    expect(
      within(screen.getByRole('dialog')).getByText('System prompt'),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: 'Close'}));

    rerender(
      <ConversationMessage
        actor="USER"
        content={[{contentType: 'TEXT', text: 'msg'}]}
      />,
    );
    await user.click(screen.getByRole('button', {name: 'Expand'}));
    expect(
      within(screen.getByRole('dialog')).getByText('User message'),
    ).toBeInTheDocument();
  });

  it('should reset to preview mode when modal is reopened', async () => {
    const {user} = render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[{contentType: 'TEXT', text: 'Hello **world**'}]}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Expand'}));
    await user.click(screen.getByRole('tab', {name: 'Source'}));
    await user.click(screen.getByRole('button', {name: 'Close'}));

    await user.click(screen.getByRole('button', {name: 'Expand'}));
    expect(
      within(screen.getByRole('dialog')).getByRole('tab', {
        name: 'Preview',
        selected: true,
      }),
    ).toBeInTheDocument();
  });

  it('should show Preview and Source tabs for plain text messages too', async () => {
    const {user} = render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[{contentType: 'TEXT', text: 'Just plain text'}]}
      />,
    );

    await user.click(screen.getByRole('button', {name: 'Expand'}));

    const modal = screen.getByRole('dialog');
    expect(
      within(modal).getByRole('tab', {name: 'Preview'}),
    ).toBeInTheDocument();
    expect(
      within(modal).getByRole('tab', {name: 'Source'}),
    ).toBeInTheDocument();
  });

  it.each([
    ['without a payload', undefined],
    ['with a null payload', null],
    ['with a non-object payload', 'opaque'],
    ['with a provider-specific payload', {type: 'thinking', data: 'opaque'}],
  ])('should render assistant reasoning %s', (_description, payload) => {
    render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[
          {
            contentType: 'OBJECT',
            object: {
              'camunda.agenticai.content.type': 'reasoning',
              provider: 'openai',
              payload,
              text: 'Check the policy before estimating the claim.',
            },
          },
        ]}
      />,
    );

    expect(screen.getByText('Thinking')).toBeVisible();
    expect(
      screen.getByText('Check the policy before estimating the claim.'),
    ).toBeVisible();
    expect(screen.queryByText('opaque')).not.toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('should render full multiline reasoning without exposing its payload or actions', () => {
    const reasoning = [
      'Review the complete evidence before deciding.',
      'A'.repeat(500),
    ].join('\n');

    render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[
          {
            contentType: 'OBJECT',
            object: {
              'camunda.agenticai.content.type': 'reasoning',
              provider: 'anthropic',
              payload: {signature: 'provider-secret'},
              text: reasoning,
            },
          },
        ]}
      />,
    );

    const note = screen.getByRole('region', {name: 'Thinking'});
    expect(
      within(note).getByText(/Review the complete evidence/).textContent,
    ).toBe(reasoning);
    expect(screen.queryByText('provider-secret')).not.toBeInTheDocument();
    expect(within(note).queryByRole('button')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Expand'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Copy to clipboard'}),
    ).not.toBeInTheDocument();
  });

  it.each([
    ['missing discriminator', {text: 'Fallback content'}],
    [
      'wrong discriminator',
      {
        'camunda.agenticai.content.type': 'analysis',
        text: 'Fallback content',
      },
    ],
    [
      'legacy discriminator',
      {
        '@type': 'camunda.aiagent.model.thinking',
        text: 'Fallback content',
      },
    ],
    [
      'blank text',
      {
        'camunda.agenticai.content.type': 'reasoning',
        text: ' \n\t ',
      },
    ],
    [
      'missing text',
      {
        'camunda.agenticai.content.type': 'reasoning',
      },
    ],
    [
      'non-string text',
      {
        'camunda.agenticai.content.type': 'reasoning',
        text: 42,
      },
    ],
    ['array value', ['reasoning', 'Fallback content']],
    ['unrelated object', {kind: 'metadata', value: 'Fallback content'}],
  ])(
    'should preserve generic object rendering for %s',
    (_description, object) => {
      render(
        <ConversationMessage
          actor="ASSISTANT"
          content={[{contentType: 'OBJECT', object}]}
        />,
      );

      const genericObject = screen.getByText(
        (_content, element) => element?.tagName === 'PRE',
      );
      expect(genericObject.textContent).toBe(JSON.stringify(object, null, 2));
      expect(screen.queryByText('Thinking')).not.toBeInTheDocument();
      expect(screen.getByRole('button', {name: 'Expand'})).toBeInTheDocument();
      expect(
        screen.getByRole('button', {name: 'Copy to clipboard'}),
      ).toBeInTheDocument();
    },
  );

  it('should only render reasoning inline for assistant records', () => {
    render(
      <ConversationMessage
        actor="USER"
        content={[
          {
            contentType: 'OBJECT',
            object: {
              'camunda.agenticai.content.type': 'reasoning',
              text: 'Assistant-only reasoning',
            },
          },
        ]}
      />,
    );

    expect(
      screen.getByText(/"text": "Assistant-only reasoning"/),
    ).toBeVisible();
    expect(screen.queryByText('Thinking')).not.toBeInTheDocument();
  });

  it('should preserve content, attachment, tool call, and metric ordering', () => {
    render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[
          {contentType: 'TEXT', text: 'Answer introduction'},
          {
            contentType: 'OBJECT',
            object: {
              'camunda.agenticai.content.type': 'reasoning',
              text: 'Reasoning between message content and attachments',
            },
          },
          createDocumentContent(),
        ]}
        metrics={{
          inputTokens: 2,
          outputTokens: 1,
          cacheCreationTokenCount: null,
          cacheReadTokenCount: null,
          reasoningTokenCount: 1,
          durationMs: 1500,
        }}
        toolCalls={[
          {
            toolCallId: 'tool-call',
            toolName: 'inspect_evidence',
            elementId: null,
            arguments: null,
          },
        ]}
      />,
    );

    const metric = screen.getByTestId('message-token-metric');
    const text = screen.getByText('Answer introduction');
    const reasoning = screen.getByRole('region', {name: 'Thinking'});
    const document = screen.getByRole('listitem', {name: 'evidence.txt'});
    const toolCall = screen.getByRole('listitem', {name: 'inspect_evidence'});

    expect(metric).toHaveTextContent('3 tokens');
    expect(screen.getByTestId('message-duration-metric')).toHaveTextContent(
      '1.50s',
    );
    expect(
      metric.compareDocumentPosition(text) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(
      text.compareDocumentPosition(reasoning) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(
      reasoning.compareDocumentPosition(document) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(
      document.compareDocumentPosition(toolCall) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it('should leave thinking XML in ordinary assistant text unstructured', () => {
    render(
      <ConversationMessage
        actor="ASSISTANT"
        content={[
          {
            contentType: 'TEXT',
            text: '<thinking><context>Temporary context</context><reflection>Temporary reasoning</reflection></thinking>',
          },
        ]}
      />,
    );

    expect(screen.queryByText('Thinking')).not.toBeInTheDocument();
    expect(screen.queryByText('Reasoning')).not.toBeInTheDocument();
  });
});
