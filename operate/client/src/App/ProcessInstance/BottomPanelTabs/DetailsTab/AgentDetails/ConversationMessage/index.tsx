/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useReducer, useState, lazy, Suspense} from 'react';
import {Button, Modal, Switch, Tag, Tooltip} from '@carbon/react';
import {Document, Maximize} from '@carbon/react/icons';
import type {
  AgentInstanceHistoryItem,
  AgentInstanceHistoryRole,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {CopyButton} from 'modules/components/CopyButton';
import {
  Container,
  MessageBlock,
  ActorLabel,
  TextContent,
  MessageActions,
  ObjectContent,
  AttachmentsContainer,
  AttachmentsLabel,
  AttachmentButton,
  MessageHeader,
  MetricsContainer,
  ModalContent,
  ModalToolbar,
  ViewSwitcher,
} from './styled';
import {MarkdownMessage} from './MarkdownMessage';
import {containsMarkdown} from './containsMarkdown';

type Actor = AgentInstanceHistoryRole | 'SYSTEM';
type ContentItem = AgentInstanceHistoryItem['content'][number];
type ToolCall = AgentInstanceHistoryItem['toolCalls'][number];
type Metrics = AgentInstanceHistoryItem['metrics'];

const readableTitleByActor: Record<Actor, string> = {
  SYSTEM: 'System prompt',
  USER: 'User message',
  ASSISTANT: 'Assistant message',
  TOOL_RESULT: 'Tool result',
};

const labelByActor: Record<Actor, string> = {
  SYSTEM: 'System',
  USER: 'User',
  ASSISTANT: 'Assistant',
  TOOL_RESULT: 'Tool Result',
};

function formatDuration(ms: number): string {
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(2)}s`;
}

type ConversationMessageProps = {
  actor: Actor;
  content: ContentItem[];
  historyItemKey?: string;
  metrics?: Metrics | null;
  toolCalls?: ToolCall[];
  onToolCallClick?: (toolCall: ToolCall) => void;
};

const RichTextEditor = lazy(async () => {
  const [{loadMonaco}, {RichTextEditor}] = await Promise.all([
    import('modules/loadMonaco'),
    import('modules/components/RichTextEditor'),
  ]);

  loadMonaco();

  return {default: RichTextEditor};
});

const ConversationMessage: React.FC<ConversationMessageProps> = ({
  actor,
  content,
  historyItemKey,
  metrics = null,
  toolCalls = [],
  onToolCallClick,
}) => {
  const [messageDetails, dispatch] = useReducer(
    messageDetailsReducer,
    initialMessageDetailsState,
  );
  const [modalView, setModalView] = useState<'preview' | 'source'>('preview');

  const documentEntries = content.filter(
    (entry) => entry.contentType === 'DOCUMENT',
  );

  return (
    <Container
      $actor={actor}
      data-testid={`conversation-message${historyItemKey ? `-${historyItemKey}` : ''}`}
      aria-label={readableTitleByActor[actor]}
    >
      <MessageHeader>
        <ActorLabel>{labelByActor[actor]}</ActorLabel>
        {metrics !== null && (
          <MetricsContainer>
            <Tooltip
              description={`Input: ${metrics.inputTokens.toLocaleString()} · Output: ${metrics.outputTokens.toLocaleString()}`}
              align="bottom"
            >
              <Tag data-testid="message-token-metric" type="gray" size="sm">
                {(metrics.inputTokens + metrics.outputTokens).toLocaleString()}
                &nbsp;tokens
              </Tag>
            </Tooltip>
            <Tag data-testid="message-duration-metric" type="gray" size="sm">
              {formatDuration(metrics.durationMs)}
            </Tag>
          </MetricsContainer>
        )}
      </MessageHeader>
      {content.map((entry, index) => {
        switch (entry.contentType) {
          case 'DOCUMENT': {
            return null;
          }
          case 'OBJECT': {
            const value = JSON.stringify(entry.object, null, 2);
            return (
              <MessageContent
                key={index}
                value={value}
                onExpandClick={() =>
                  dispatch({type: 'show-object', actor, value})
                }
              >
                <ObjectContent>{value}</ObjectContent>
              </MessageContent>
            );
          }
          case 'TEXT': {
            return (
              <MessageContent
                key={index}
                value={entry.text}
                onExpandClick={() =>
                  dispatch({type: 'show-text', actor, text: entry.text})
                }
              >
                <TextContent>
                  <MarkdownMessage content={entry.text} />
                </TextContent>
              </MessageContent>
            );
          }
        }
      })}
      {documentEntries.length > 0 && (
        <AttachmentsContainer>
          <AttachmentsLabel>Documents</AttachmentsLabel>
          {documentEntries.map(({documentReference}) => (
            <AttachmentButton key={documentReference.documentId} disabled>
              <Document size={12} />
              {documentReference.metadata.fileName}
            </AttachmentButton>
          ))}
        </AttachmentsContainer>
      )}
      {toolCalls.length > 0 && (
        <AttachmentsContainer>
          <AttachmentsLabel>Tool calls</AttachmentsLabel>
          {toolCalls.map((tc) => {
            const isDisabled = tc.elementId === null;
            const label = isDisabled
              ? `"${tc.toolName}" tool call.`
              : `"${tc.toolName}" tool call. Click to open details.`;
            return (
              <AttachmentButton
                key={tc.toolCallId}
                aria-label={label}
                disabled={isDisabled}
                onClick={() => onToolCallClick?.(tc)}
              >
                {tc.toolName}
              </AttachmentButton>
            );
          })}
        </AttachmentsContainer>
      )}
      {messageDetails.state === 'visible' && (
        <Modal
          open
          modalHeading={messageDetails.title}
          onRequestClose={() => {
            dispatch({type: 'hide'});
            setModalView('preview');
          }}
          size="lg"
          passiveModal
        >
          {messageDetails.language === 'markdown' &&
          containsMarkdown(messageDetails.content) ? (
            <>
              <ModalToolbar>
                <ViewSwitcher
                  size="sm"
                  onChange={({name}) =>
                    setModalView(name as 'preview' | 'source')
                  }
                  selectedIndex={modalView === 'preview' ? 0 : 1}
                >
                  <Switch name="preview" text="Preview" />
                  <Switch name="source" text="Source" />
                </ViewSwitcher>
                <CopyButton value={messageDetails.content} />
              </ModalToolbar>
              {modalView === 'preview' ? (
                <ModalContent>
                  <MarkdownMessage content={messageDetails.content} />
                </ModalContent>
              ) : (
                <Suspense>
                  <RichTextEditor
                    value={messageDetails.content}
                    readOnly
                    language="markdown"
                  />
                </Suspense>
              )}
            </>
          ) : (
            <Suspense>
              <RichTextEditor
                value={messageDetails.content}
                readOnly
                language={messageDetails.language}
              />
            </Suspense>
          )}
        </Modal>
      )}
    </Container>
  );
};

type MessageContentProps = {
  value: string;
  children: React.ReactNode;
  onExpandClick: () => void;
};

const MessageContent: React.FC<MessageContentProps> = ({
  value,
  children,
  onExpandClick,
}) => {
  return (
    <MessageBlock>
      {children}
      <MessageActions>
        <Button
          kind="ghost"
          size="sm"
          renderIcon={Maximize}
          iconDescription="Expand"
          hasIconOnly
          onClick={onExpandClick}
        />
        <CopyButton value={value} hasIconOnly tooltipAlignment="end" />
      </MessageActions>
    </MessageBlock>
  );
};

type MessageDetailsState = {
  state: 'visible' | 'hidden';
  language: 'markdown' | 'json';
  content: string;
  title: string;
};
type MessageDetailsAction =
  | {type: 'show-text'; actor: Actor; text: string}
  | {type: 'show-object'; actor: Actor; value: string}
  | {type: 'hide'};

const initialMessageDetailsState: MessageDetailsState = {
  state: 'hidden',
  language: 'markdown',
  content: '',
  title: '',
};

function messageDetailsReducer(
  state: MessageDetailsState,
  action: MessageDetailsAction,
): MessageDetailsState {
  switch (action.type) {
    case 'show-text':
      return {
        state: 'visible',
        language: 'markdown',
        content: action.text,
        title: readableTitleByActor[action.actor],
      };
    case 'show-object':
      return {
        state: 'visible',
        language: 'json',
        content: action.value,
        title: readableTitleByActor[action.actor],
      };
    case 'hide':
      return initialMessageDetailsState;
    default:
      return state;
  }
}

export {ConversationMessage};
