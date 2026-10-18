/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useReducer} from 'react';
import {Button} from '@carbon/react';
import {Maximize} from '@carbon/react/icons';
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
  MessageHeader,
} from './styled';
import {StructuredText} from './StructuredValue/StructuredText';
import {MessageDetailsModal} from './MessageDetailsModal';
import {MessageMetrics} from './MessageMetrics';
import {DocumentContent} from './MessageAttachments/DocumentContent';
import {ToolCalls} from './MessageAttachments/ToolCalls';
import {EvidenceCards} from './EvidenceCard';
import {isEvidenceOnly} from './EvidenceCard/evidence';
import {StructuredValue} from './StructuredValue';
import {
  isEmptyValue,
  omitEvidence,
  parseStructuredText,
  stripDocumentTags,
} from './StructuredValue/format';

type Actor =
  Exclude<AgentInstanceHistoryRole, 'TOOL_RESULT' | 'CONFIGURATION'> | 'SYSTEM';
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
  label?: string;
  credentialBinding?: string | null;
};

const ConversationMessage: React.FC<ConversationMessageProps> = ({
  actor,
  content,
  historyItemKey,
  metrics = null,
  toolCalls = [],
  label,
  credentialBinding,
}) => {
  const [messageDetails, dispatch] = useReducer(
    messageDetailsReducer,
    initialMessageDetailsState,
  );

  return (
    <Container
      $actor={actor}
      data-testid={`conversation-message${historyItemKey ? `-${historyItemKey}` : ''}`}
      aria-label={readableTitleByActor[actor]}
    >
      <MessageHeader>
        <ActorLabel>{label ?? labelByActor[actor]}</ActorLabel>
        <MessageMetrics metrics={metrics} />
      </MessageHeader>
      {content.map((entry, index) => {
        switch (entry.contentType) {
          case 'DOCUMENT': {
            return null;
          }
          case 'OBJECT': {
            if (
              isEvidenceOnly(entry) ||
              isEmptyValue(omitEvidence(entry.object))
            ) {
              return null;
            }
            return (
              <StructuredContent
                key={index}
                value={entry.object}
                onExpandClick={() =>
                  dispatch({type: 'show-object', actor, value: entry.object})
                }
              />
            );
          }
          case 'TEXT': {
            const text = stripDocumentTags(entry.text);
            if (text === '') {
              return null;
            }
            const structured = parseStructuredText(text);
            if (structured !== null) {
              return (
                <StructuredContent
                  key={index}
                  value={structured}
                  onExpandClick={() =>
                    dispatch({type: 'show-object', actor, value: structured})
                  }
                />
              );
            }
            return (
              <MessageContent
                key={index}
                value={text}
                onExpandClick={() => dispatch({type: 'show-text', actor, text})}
              >
                <TextContent>
                  <StructuredText content={text} />
                </TextContent>
              </MessageContent>
            );
          }
        }
      })}
      <DocumentContent
        content={content}
        modalTitleSuffix="conversation message"
      />
      <EvidenceCards content={content} credentialBinding={credentialBinding} />
      <ToolCalls toolCalls={toolCalls} />
      {messageDetails.state === 'visible' && (
        <MessageDetailsModal
          title={messageDetails.title}
          language={messageDetails.language}
          content={messageDetails.content}
          value={messageDetails.value}
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

type StructuredContentProps = {
  value: unknown;
  onExpandClick: () => void;
};

const StructuredContent: React.FC<StructuredContentProps> = ({
  value,
  onExpandClick,
}) => (
  <MessageContent
    value={JSON.stringify(omitEvidence(value), null, 2)}
    onExpandClick={onExpandClick}
  >
    <StructuredValue value={value} />
  </MessageContent>
);

type MessageDetailsState = {
  state: 'visible' | 'hidden';
  language: 'markdown' | 'structured';
  content: string;
  value?: unknown;
  title: string;
};
type MessageDetailsAction =
  | {type: 'show-text'; actor: Actor; text: string}
  | {type: 'show-object'; actor: Actor; value: unknown}
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
        language: 'structured',
        content: JSON.stringify(omitEvidence(action.value), null, 2),
        value: action.value,
        title: readableTitleByActor[action.actor],
      };
    case 'hide':
      return initialMessageDetailsState;
    default:
      return state;
  }
}

export {ConversationMessage};
