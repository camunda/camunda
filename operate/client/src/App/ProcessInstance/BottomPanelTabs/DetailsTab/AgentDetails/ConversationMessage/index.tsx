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
import {ReasoningNote} from './ReasoningNote';
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
  onExpand?: () => void;
};

const ConversationMessage: React.FC<ConversationMessageProps> = ({
  actor,
  content,
  historyItemKey,
  metrics = null,
  toolCalls = [],
  label,
  onExpand,
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
        {onExpand && (
          <Button
            kind="ghost"
            size="sm"
            renderIcon={Maximize}
            iconDescription="Expand"
            data-history-expand
            hasIconOnly
            onClick={onExpand}
          />
        )}
      </MessageHeader>
      {content.map((entry, index) => {
        switch (entry.contentType) {
          case 'DOCUMENT': {
            return null;
          }
          case 'OBJECT': {
            if (actor === 'ASSISTANT' && isReasoning(entry.object)) {
              const reasoningText = getReasoningText(entry.object);

              return reasoningText === null ? null : (
                <ReasoningNote key={index} reasoning={reasoningText} />
              );
            }

            if (
              isEvidenceOnly(entry) ||
              isEmptyValue(omitEvidence(entry.object))
            ) {
              return null;
            }
            return (
              <StructuredContent
                key={index}
                expanded={onExpand !== undefined}
                value={entry.object}
                onExpandClick={() =>
                  onExpand
                    ? onExpand()
                    : dispatch({
                        type: 'show-object',
                        actor,
                        value: entry.object,
                      })
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
                  expanded={onExpand !== undefined}
                  value={structured}
                  onExpandClick={() =>
                    onExpand
                      ? onExpand()
                      : dispatch({
                          type: 'show-object',
                          actor,
                          value: structured,
                        })
                  }
                />
              );
            }
            return (
              <MessageContent
                key={index}
                expanded={onExpand !== undefined}
                value={text}
                onExpandClick={() =>
                  onExpand
                    ? onExpand()
                    : dispatch({type: 'show-text', actor, text})
                }
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
      <EvidenceCards content={content} />
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

function isReasoning(value: unknown): value is Record<string, unknown> {
  return (
    isPlainObject(value) &&
    value['camunda.agenticai.content.type'] === 'reasoning'
  );
}

function getReasoningText(value: Record<string, unknown>): string | null {
  if (typeof value['text'] !== 'string') {
    return null;
  }

  return value['text'].trim() === '' ? null : value['text'];
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return false;
  }

  const prototype = Object.getPrototypeOf(value);
  return prototype === Object.prototype || prototype === null;
}

type MessageContentProps = {
  value: string;
  children: React.ReactNode;
  onExpandClick: () => void;
  expanded?: boolean;
};

const MessageContent: React.FC<MessageContentProps> = ({
  value,
  children,
  onExpandClick,
  expanded,
}) => {
  return (
    <MessageBlock>
      {children}
      <MessageActions>
        {!expanded && (
          <Button
            kind="ghost"
            size="sm"
            renderIcon={Maximize}
            iconDescription="Expand"
            hasIconOnly
            onClick={onExpandClick}
          />
        )}
        <CopyButton value={value} hasIconOnly tooltipAlignment="end" />
      </MessageActions>
    </MessageBlock>
  );
};

type StructuredContentProps = {
  value: unknown;
  onExpandClick: () => void;
  expanded?: boolean;
};

const StructuredContent: React.FC<StructuredContentProps> = ({
  value,
  onExpandClick,
  expanded,
}) => (
  <MessageContent
    value={JSON.stringify(omitEvidence(value), null, 2)}
    onExpandClick={onExpandClick}
    expanded={expanded}
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
