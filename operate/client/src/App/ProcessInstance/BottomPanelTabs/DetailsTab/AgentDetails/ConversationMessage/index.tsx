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
  ObjectContent,
} from './styled';
import {MarkdownMessage} from './MarkdownMessage';
import {RichTextEditorModal} from 'modules/components/RichTextEditorModal';

type Actor = AgentInstanceHistoryRole | 'SYSTEM';
type ContentItem = AgentInstanceHistoryItem['content'][number];

const editorModalTitleByActor: Record<Actor, string> = {
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

type ConversationMessageProps = {
  actor: Actor;
  content: ContentItem[];
  historyItemKey?: string;
};

const ConversationMessage: React.FC<ConversationMessageProps> = ({
  actor,
  content,
  historyItemKey,
}) => {
  const [messageDetails, dispatch] = useReducer(
    messageDetailsReducer,
    initialMessageDetailsState,
  );

  return (
    <Container
      $actor={actor}
      data-testid={`conversation-message-${historyItemKey}`}
    >
      <ActorLabel>{labelByActor[actor]}</ActorLabel>
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
      <RichTextEditorModal
        title={messageDetails.title}
        readOnly
        language={messageDetails.language}
        value={messageDetails.content}
        isVisible={messageDetails.state === 'visible'}
        onClose={() => dispatch({type: 'hide'})}
      />
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
        title: editorModalTitleByActor[action.actor],
      };
    case 'show-object':
      return {
        state: 'visible',
        language: 'json',
        content: action.value,
        title: editorModalTitleByActor[action.actor],
      };
    case 'hide':
      return initialMessageDetailsState;
    default:
      return state;
  }
}

export {ConversationMessage};
