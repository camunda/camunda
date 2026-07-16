/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useReducer} from 'react';
import {Button} from '@carbon/react';
import {Document, Maximize, Tools} from '@carbon/react/icons';
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
} from './styled';
import {MarkdownMessage} from './MarkdownMessage';
import {MessageDetailsModal} from './MessageDetailsModal';
import {MessageMetrics} from './MessageMetrics';

type Actor = Exclude<AgentInstanceHistoryRole, 'TOOL_RESULT'> | 'SYSTEM';
type ContentItem = AgentInstanceHistoryItem['content'][number];
type ToolCall = AgentInstanceHistoryItem['toolCalls'][number];
type Metrics = AgentInstanceHistoryItem['metrics'];

const readableTitleByActor: Record<Actor, string> = {
  SYSTEM: 'System prompt',
  USER: 'User message',
  ASSISTANT: 'Assistant message',
};

const labelByActor: Record<Actor, string> = {
  SYSTEM: 'System',
  USER: 'User',
  ASSISTANT: 'Assistant',
};

type ConversationMessageProps = {
  actor: Actor;
  content: ContentItem[];
  historyItemKey?: string;
  metrics?: Metrics;
  toolCalls?: ToolCall[];
};

const ConversationMessage: React.FC<ConversationMessageProps> = ({
  actor,
  content,
  historyItemKey,
  metrics = null,
  toolCalls = [],
}) => {
  const [messageDetails, dispatch] = useReducer(
    messageDetailsReducer,
    initialMessageDetailsState,
  );

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
        <MessageMetrics metrics={metrics} />
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
          {toolCalls.map((tc) => (
            <AttachmentButton
              key={tc.toolCallId}
              aria-label={`"${tc.toolName}" tool call.`}
              disabled
            >
              <Tools size={12} />
              {tc.toolName}
            </AttachmentButton>
          ))}
        </AttachmentsContainer>
      )}
      {messageDetails.state === 'visible' && (
        <MessageDetailsModal
          title={messageDetails.title}
          language={messageDetails.language}
          content={messageDetails.content}
          onClose={() => dispatch({type: 'hide'})}
        />
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
