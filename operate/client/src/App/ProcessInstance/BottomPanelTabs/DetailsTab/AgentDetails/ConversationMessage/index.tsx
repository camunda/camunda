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
import {CopyButton} from 'modules/components/CopyButton';
import {
  Container,
  MessageBlock,
  ActorLabel,
  Message,
  MessageActions,
} from './styled';
import {RichTextEditorModal} from 'modules/components/RichTextEditorModal';

type Actor = 'user' | 'assistant' | 'system';

const editorModalTitleByActor: Record<Actor, string> = {
  system: 'System prompt',
  user: 'User message',
  assistant: 'Assistant message',
};

const labelByActor: Record<Actor, string> = {
  system: 'System',
  user: 'User',
  assistant: 'Assistant',
};

type ConversationMessageProps = {
  actor: Actor;
  messages: string[];
};

const ConversationMessage: React.FC<ConversationMessageProps> = ({
  actor,
  messages,
}) => {
  const [messageDetails, dispatch] = useReducer(
    messageDetailsReducer,
    initialMessageDetailsState,
  );

  return (
    <Container $actor={actor}>
      <ActorLabel>{labelByActor[actor]}</ActorLabel>
      {messages.map((message, index) => (
        <MessageBlock key={index}>
          <Message>{message}</Message>
          <MessageActions>
            <Button
              kind="ghost"
              size="sm"
              renderIcon={Maximize}
              iconDescription="Expand"
              hasIconOnly
              onClick={() => dispatch({type: 'show', actor, message: message})}
            />
            <CopyButton value={message} hasIconOnly tooltipAlignment="end" />
          </MessageActions>
        </MessageBlock>
      ))}
      <RichTextEditorModal
        title={messageDetails.title}
        language="markdown"
        readOnly
        value={messageDetails.message}
        isVisible={messageDetails.state === 'visible'}
        onClose={() => dispatch({type: 'hide'})}
      />
    </Container>
  );
};

type MessageDetailsState = {
  state: 'visible' | 'hidden';
  message: string;
  title: string;
};
type MessageDetailsAction =
  | {type: 'show'; actor: Actor; message: string}
  | {type: 'hide'};

const initialMessageDetailsState: MessageDetailsState = {
  state: 'hidden',
  message: '',
  title: '',
};

function messageDetailsReducer(
  state: MessageDetailsState,
  action: MessageDetailsAction,
): MessageDetailsState {
  switch (action.type) {
    case 'show':
      return {
        state: 'visible',
        message: action.message,
        title: editorModalTitleByActor[action.actor],
      };
    case 'hide':
      return initialMessageDetailsState;
    default:
      return state;
  }
}

export {ConversationMessage};
