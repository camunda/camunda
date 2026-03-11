/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {Button, InlineLoading, SelectableTag, Stack} from '@carbon/react';
import {styles} from '@carbon/elements';

// ─── Message content layout ───────────────────────────────────────────────────

const MessageContentWrapper = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);
`;

const MessageParagraph = styled.p`
  ${styles.bodyLong01};
  color: inherit;
  margin: 0;
`;

const MessageSectionHeader = styled.p`
  ${styles.bodyLong01};
  font-weight: 600;
  color: var(--cds-text-primary);
  margin: 0;
`;

const listStyles = css`
  padding-left: var(--cds-spacing-06);
  margin: 0;

  li {
    ${styles.bodyLong01};
    color: inherit;
    margin-bottom: var(--cds-spacing-02);

    &:last-child {
      margin-bottom: 0;
    }
  }
`;

const MessageOrderedList = styled.ol`
  ${listStyles}
  list-style-type: decimal;

  li {
    margin-bottom: var(--cds-spacing-04);
  }
`;

const MessageUnorderedList = styled.ul`
  ${listStyles}
  list-style-type: disc;
`;

const SuggestedActionsBar = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: var(--cds-spacing-03);
  padding-top: var(--cds-spacing-03);
`;

const MAX_TEXTAREA_HEIGHT = 150;

const BUTTON_WRAPPER_SIZE = 'var(--cds-spacing-08)';

const ChatboxTile = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  background-color: var(--cds-background);
  width: 390px;
  overflow: hidden;
`;

const ChatboxHeader = styled.div`
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  max-height: 64px;
  width: 100%;
`;

const CamundaCopilotHeader = styled.span`
  color: var(--cds-text-primary);
  ${styles.headingCompact01};
`;

const HeaderGroup = styled(Stack)`
  align-items: center;
`;

const HeaderSpace = styled.div`
  flex-grow: 1;
`;

const CloseButton = styled(Button)`
  color: var(--cds-icon-primary);
`;

const ChatboxBody = styled.div`
  padding-top: 0;
  flex-grow: 1;
  width: 100%;
  flex-direction: column;
  align-items: stretch;
  justify-content: space-between;
  overflow-y: auto;
  overflow-x: hidden;
`;

const MessagesContainer = styled.div`
  display: flex;
  flex-direction: column;
  flex: 1;
  align-items: center;
  height: 100%;
  background-color: var(--cds-background);
`;

const MessagesBody = styled.div`
  padding: var(--cds-spacing-04) var(--cds-spacing-05);
  overflow-y: auto;
  height: 100%;
  width: 100%;
`;

const MessagesStack = styled(Stack)`
  gap: var(--cds-spacing-05);
`;

type MessageStackItemProps = {
  $type: 'human' | 'ai';
};

const MessageStackItem = styled.div<MessageStackItemProps>`
  display: flex;
  justify-content: ${({$type}) =>
    $type === 'ai' ? 'flex-start' : 'flex-end'};

  > div {
    max-width: ${({$type}) => ($type === 'human' ? '80%' : '90%')};
  }
`;

type MessageContainerProps = {
  $type: 'human' | 'ai';
};

const MessageContainer = styled.div<MessageContainerProps>`
  display: flex;
  flex-direction: row;
`;

type ChatMessageBubbleProps = {
  $type: 'human' | 'ai';
  $isProcessing?: boolean;
};

const ChatMessageBubble = styled.div<ChatMessageBubbleProps>`
  word-break: break-word;
  color: var(--cds-text-primary);

  a {
    color: var(--cds-text-primary);
  }

  ${({$isProcessing}) =>
    $isProcessing
      ? css`
          padding: 1px 3px 1px 9px;
        `
      : css`
          flex: 1;
        `}

  ${({$type, $isProcessing}) =>
    $type === 'ai' &&
    !$isProcessing &&
    css`
      background-color: var(--cds-layer-01);
      border-radius: 4px;
      padding: 8px;
    `}

  ${({$type}) =>
    $type === 'human' &&
    css`
      background-color: var(--cds-border-subtle-01);
      border-radius: 4px;
      padding: 8px;
    `}
`;

const CopilotInlineLoading = styled(InlineLoading)``;

const CopilotInlineLoadingText = styled.span`
  color: var(--cds-text-secondary);
`;

// Intro container styles
const IntroContainer = styled.div`
  height: 100%;
  padding: var(--cds-spacing-04);
  display: flex;
  gap: var(--cds-spacing-05);
  flex-direction: column;
  justify-content: center;
  align-content: center;
`;

const IntroText = styled.span`
  display: flex;
  gap: var(--cds-spacing-04);
  flex-direction: column;
  text-align: center;
  color: var(--cds-text-secondary);

  p {
    display: flex;
    justify-content: center;
    gap: var(--cds-spacing-02);
    margin: 0;
    font-weight: 400;
  }

  strong {
    font-weight: 600;
    line-height: 1.15rem;
  }
`;

const IntroDescription = styled.span`
  ${styles.bodyShort01};
  color: var(--cds-text-secondary);
  text-align: center;
`;

const ActionButtonGroup = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: var(--cds-spacing-04);
  justify-content: center;
`;

const StyledSelectableTag = styled(SelectableTag)`
  && {
    max-width: 100%;
    white-space: normal;
    height: auto;
    text-align: left;
    padding-block: var(--cds-spacing-02);
  }
  margin: 0;
`;

// TextArea and submit styles
type ChatboxFormProps = {
  $variant?: 'intro' | 'chat';
};

const ChatboxForm = styled.div<ChatboxFormProps>`
  position: relative;
  display: flex;
  flex-direction: row;
  align-items: flex-end;
  background-color: var(--cds-field-01);
  width: calc(100% - 2 * var(--cds-spacing-04));
  margin: 0 var(--cds-spacing-04);

  ${({$variant}) =>
    $variant === 'chat' &&
    `
    margin: var(--cds-spacing-04);
    width: calc(100% - 2 * var(--cds-spacing-04));
    border: 1px solid var(--cds-border-subtle-03);
  `}
`;

const ButtonWrapper = styled.div`
  position: absolute;
  right: 0;
  width: ${BUTTON_WRAPPER_SIZE};
  height: ${BUTTON_WRAPPER_SIZE};
  text-align: center;
  display: flex;
  justify-content: center;
  align-items: center;
`;

type StyledTextAreaProps = {
  $variant?: 'intro' | 'chat';
};

const StyledTextArea = styled.textarea<StyledTextAreaProps>`
  background: var(--cds-field-01);
  border: 0 none;
  padding: var(--cds-spacing-04);
  width: 100%;
  resize: none;
  max-height: ${MAX_TEXTAREA_HEIGHT}px;
  color: var(--cds-text-primary);
  font-family: inherit;
  outline: none;

  &:focus {
    outline: 2px solid var(--cds-focus);
    outline-offset: -2px;
  }

  &::placeholder {
    color: var(--cds-text-secondary);
    font-weight: 400;
    line-height: 1rem;
  }
`;

const AuditEntry = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-01);
  background-color: var(--cds-layer-02);
  border-left: 2px solid var(--cds-border-interactive);
  border-radius: 0 2px 2px 0;
  padding: var(--cds-spacing-02) var(--cds-spacing-03);
  font-family: 'IBM Plex Mono', monospace;
  word-break: break-word;
`;

const AuditEntryMeta = styled.span`
  font-size: 0.7rem;
  color: var(--cds-text-secondary);
  letter-spacing: 0.01em;
`;

const AuditEntryAction = styled.span`
  font-size: 0.75rem;
  color: var(--cds-text-primary);
`;

const AuditEntryDetail = styled.span`
  font-size: 0.7rem;
  color: var(--cds-text-secondary);
`;

// Copilot sparkle icon styled for the panel header
const CopilotIconWrapper = styled.span`
  display: inline-flex;
  align-items: center;
  color: #8a3ffc;

  svg {
    width: 16px;
    height: 14px;
  }
`;

export {
  MAX_TEXTAREA_HEIGHT,
  MessageContentWrapper,
  MessageParagraph,
  MessageSectionHeader,
  MessageOrderedList,
  MessageUnorderedList,
  SuggestedActionsBar,
  ChatboxTile,
  ChatboxHeader,
  CamundaCopilotHeader,
  HeaderGroup,
  HeaderSpace,
  CloseButton,
  ChatboxBody,
  MessagesContainer,
  MessagesBody,
  MessagesStack,
  MessageStackItem,
  MessageContainer,
  ChatMessageBubble,
  CopilotInlineLoading,
  CopilotInlineLoadingText,
  IntroContainer,
  IntroText,
  IntroDescription,
  ActionButtonGroup,
  StyledSelectableTag,
  ChatboxForm,
  ButtonWrapper,
  StyledTextArea,
  CopilotIconWrapper,
  AuditEntry,
  AuditEntryMeta,
  AuditEntryAction,
  AuditEntryDetail,
};
