/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css, keyframes} from 'styled-components';

const ChatbotContainer = styled.div`
  position: fixed;
  bottom: var(--cds-spacing-05);
  right: var(--cds-spacing-05);
  z-index: 9999;
`;

const ChatbotToggle = styled.div`
  display: flex;
  justify-content: flex-end;

  button {
    width: 48px;
    height: 48px;
    border-radius: 50%;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    transition: transform 0.2s ease;

    &:hover {
      transform: scale(1.05);
    }
  }
`;

const ChatWindow = styled.div`
  position: absolute;
  bottom: 64px;
  right: 0;
  width: 400px;
  max-width: calc(100vw - var(--cds-spacing-10));
  height: 500px;
  max-height: calc(100vh - 120px);
  background: var(--cds-layer);
  border: 1px solid var(--cds-border-subtle);
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  overflow: hidden;
`;

const ChatHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--cds-spacing-04) var(--cds-spacing-05);
  background: var(--cds-layer-accent);
  border-bottom: 1px solid var(--cds-border-subtle);

  .header-content {
    display: flex;
    align-items: center;
    gap: var(--cds-spacing-03);
    font-weight: 600;
    color: var(--cds-text-primary);
  }

  .header-actions {
    display: flex;
    gap: var(--cds-spacing-02);
  }
`;

const ChatMessages = styled.div`
  flex: 1;
  overflow-y: auto;
  padding: var(--cds-spacing-05);
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-04);

  .welcome-message {
    background: var(--cds-layer-accent);
    padding: var(--cds-spacing-05);
    border-radius: 8px;
    color: var(--cds-text-secondary);

    p {
      margin: 0 0 var(--cds-spacing-03) 0;
    }

    ul {
      margin: 0;
      padding-left: var(--cds-spacing-05);

      li {
        margin-bottom: var(--cds-spacing-02);
      }
    }
  }

  .error-message {
    background: var(--cds-support-error);
    color: var(--cds-text-on-color);
    padding: var(--cds-spacing-03) var(--cds-spacing-04);
    border-radius: 8px;
    font-size: var(--cds-body-compact-01-font-size);
  }
`;

const MessageBubble = styled.div<{$role: 'user' | 'assistant' | 'system'}>`
  max-width: 85%;
  padding: var(--cds-spacing-04);
  border-radius: 12px;
  word-wrap: break-word;

  ${({$role}) =>
    $role === 'user'
      ? css`
          align-self: flex-end;
          background: var(--cds-button-primary);
          color: var(--cds-text-on-color);

          .message-content {
            white-space: pre-wrap;
          }
        `
      : css`
          align-self: flex-start;
          background: var(--cds-layer-accent);
          color: var(--cds-text-primary);

          .message-content {
            white-space: pre-wrap;
          }
        `}

  .tool-calls {
    margin-top: var(--cds-spacing-03);
    padding-top: var(--cds-spacing-03);
    border-top: 1px solid var(--cds-border-subtle);

    .tool-call {
      margin-bottom: var(--cds-spacing-02);

      .tool-name {
        font-size: var(--cds-label-01-font-size);
        font-weight: 600;
        color: var(--cds-text-secondary);
      }

      .tool-result {
        margin-top: var(--cds-spacing-02);
        padding: var(--cds-spacing-03);
        background: var(--cds-field);
        border-radius: 4px;
        font-size: var(--cds-code-01-font-size);
        font-family: var(--cds-code-01-font-family);
        overflow-x: auto;
        max-height: 150px;
        white-space: pre;
      }
    }
  }
`;

const bounce = keyframes`
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-4px);
  }
`;

const TypingIndicator = styled.div`
  display: flex;
  align-items: center;
  gap: 4px;
  padding: var(--cds-spacing-03) var(--cds-spacing-04);
  background: var(--cds-layer-accent);
  border-radius: 12px;
  align-self: flex-start;
  width: fit-content;

  span {
    width: 8px;
    height: 8px;
    background: var(--cds-text-secondary);
    border-radius: 50%;
    animation: ${bounce} 1.2s ease-in-out infinite;

    &:nth-child(1) {
      animation-delay: 0s;
    }

    &:nth-child(2) {
      animation-delay: 0.2s;
    }

    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
`;

const ChatInputArea = styled.form`
  display: flex;
  gap: var(--cds-spacing-03);
  padding: var(--cds-spacing-04);
  border-top: 1px solid var(--cds-border-subtle);
  background: var(--cds-layer);

  .cds--text-area__wrapper {
    flex: 1;
  }

  textarea {
    resize: none;
    min-height: 40px;
    max-height: 120px;
  }

  button {
    align-self: flex-end;
  }
`;

export {
  ChatbotContainer,
  ChatbotToggle,
  ChatWindow,
  ChatHeader,
  ChatMessages,
  MessageBubble,
  TypingIndicator,
  ChatInputArea,
};
