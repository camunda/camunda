/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useReducer, useState, lazy, Suspense} from 'react';
import {Button, Modal, Switch} from '@carbon/react';
import {Maximize} from '@carbon/react/icons';
import {CopyButton} from 'modules/components/CopyButton';
import {
  Container,
  MessageBlock,
  ActorLabel,
  Message,
  MessageActions,
  ModalContent,
  ModalToolbar,
  ViewSwitcher,
} from './styled';
import {MarkdownMessage} from './MarkdownMessage';
import {containsMarkdown} from './containsMarkdown';

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
  messages,
}) => {
  const [messageDetails, dispatch] = useReducer(
    messageDetailsReducer,
    initialMessageDetailsState,
  );
  const [modalView, setModalView] = useState<'preview' | 'source'>('preview');

  return (
    <Container $actor={actor}>
      <ActorLabel>{labelByActor[actor]}</ActorLabel>
      {messages.map((message, index) => (
        <MessageBlock key={index}>
          <Message>
            <MarkdownMessage content={message} />
          </Message>
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
          {containsMarkdown(messageDetails.message) ? (
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
                <CopyButton value={messageDetails.message} />
              </ModalToolbar>
              {modalView === 'preview' ? (
                <ModalContent>
                  <MarkdownMessage content={messageDetails.message} />
                </ModalContent>
              ) : (
                <Suspense>
                  <RichTextEditor
                    value={messageDetails.message}
                    readOnly
                    language="markdown"
                  />
                </Suspense>
              )}
            </>
          ) : (
            <Suspense>
              <RichTextEditor
                value={messageDetails.message}
                readOnly
                language="markdown"
              />
            </Suspense>
          )}
        </Modal>
      )}
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
