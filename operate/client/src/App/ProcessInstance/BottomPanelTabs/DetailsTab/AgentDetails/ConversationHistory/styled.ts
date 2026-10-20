/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import styled from 'styled-components';

const ConversationContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-04);

  .cds--btn-set > .cds--btn {
    inline-size: auto;
  }

  .agent-history-modal {
    align-items: flex-start;
    padding-block-start: 8vh;
  }

  .agent-history-modal .cds--modal-container {
    block-size: 84vh;
    max-block-size: 84vh;
  }

  .agent-history-modal .cds--modal-content {
    display: flex;
    flex-direction: column;
    min-block-size: 0;
  }

  .agent-history-modal .cds--modal-content > * {
    flex-shrink: 0;
  }

  .agent-history-modal .agent-message-content {
    flex: 0 0 auto;
    min-block-size: 0;
    max-block-size: none;
    overflow: visible;
  }

  .agent-history-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--cds-spacing-05);
    margin-inline-end: var(--cds-spacing-07);
  }
`;

const Messages = styled.div`
  display: flex;
  flex-direction: inherit;
  gap: inherit;
  transition: opacity 150ms ease;

  &[data-dimmed='true'] {
    opacity: 0.5;
    pointer-events: none;
  }
`;

const StatusHint = styled.span`
  font-size: var(--cds-body-compact-01-font-size);
  font-weight: var(--cds-body-compact-01-font-weight);
  line-height: var(--cds-body-compact-01-line-height);
  letter-spacing: var(--cds-body-compact-01-letter-spacing);
  text-align: center;
  color: var(--cds-text-secondary);
`;

const LoopIterationMarker = styled.span`
  display: inline-flex;
  gap: var(--cds-spacing-04);
  justify-content: center;
  align-items: center;
  font-size: var(--cds-label-01-font-size);
  font-weight: var(--cds-label-01-font-weight);
  line-height: var(--cds-label-01-line-height);
  letter-spacing: var(--cds-label-01-letter-spacing);
  color: var(--cds-text-secondary);

  &::before,
  &::after {
    content: '';
    display: inline-block;
    inline-size: 8ch;
    height: 1px;
    background-color: var(--cds-border-subtle-01);
    border-radius: 1px;
  }
`;

const ShowMoreButton = styled(Button)`
  align-self: center;
`;

const CompactionContainer = styled.article`
  border-inline-start: 2px solid var(--cds-border-strong-01);
  padding: var(--cds-spacing-03) var(--cds-spacing-05);
  color: var(--cds-text-secondary);
`;

const CompactionHeading = styled.h4`
  font-size: var(--cds-label-01-font-size);
  line-height: var(--cds-label-01-line-height);
  font-weight: var(--cds-label-01-font-weight);
`;

const CompactionMessage = styled.p`
  margin-block-start: var(--cds-spacing-02);
  font-size: var(--cds-body-compact-01-font-size);
  line-height: var(--cds-body-compact-01-line-height);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
`;

const HistoryNavigation = styled.nav`
  display: inline-flex;
  align-items: center;
  gap: var(--cds-spacing-02);
  color: var(--cds-text-secondary);
  font-size: var(--cds-label-01-font-size);
  font-weight: var(--cds-label-01-font-weight);
  white-space: nowrap;

  [role='status'] {
    min-inline-size: 4rem;
    text-align: center;
  }

  button {
    min-inline-size: 2rem;
    padding: var(--cds-spacing-03);
  }
`;

const STEP_COLORS = {
  USER: 'var(--cds-status-blue)',
  ASSISTANT: 'var(--cds-status-purple)',
  TOOL_RESULT: 'var(--cds-text-secondary)',
};

const ContentsRail = styled.nav`
  display: flex;
  flex-direction: column;
  min-inline-size: 0;
  max-block-size: 70vh;
  border-inline-end: 1px solid var(--cds-border-subtle-01);
`;

const ContentsHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--cds-spacing-02);
  padding-block-end: var(--cds-spacing-03);
  padding-inline-end: var(--cds-spacing-03);
  font-size: var(--cds-label-01-font-size);
`;

const ContentsList = styled.ol`
  list-style: none;
  margin: 0;
  padding: 0;
  min-block-size: 0;
  overflow-y: auto;
`;

const ContentsStep = styled.button<{$role: keyof typeof STEP_COLORS}>`
  display: grid;
  grid-template-columns: 1.5rem 1rem minmax(0, 1fr);
  gap: var(--cds-spacing-03);
  align-items: start;
  inline-size: 100%;
  padding: var(--cds-spacing-03) var(--cds-spacing-03);
  border: 0;
  border-inline-start: 3px solid transparent;
  background: transparent;
  color: var(--cds-text-primary);
  text-align: start;
  cursor: pointer;

  &:hover {
    background: var(--cds-layer-hover-01);
  }

  &:focus-visible {
    outline: 2px solid var(--cds-focus);
    outline-offset: -2px;
  }

  &[aria-current='step'] {
    background: var(--cds-layer-selected-01);
    border-inline-start-color: ${({$role}) => STEP_COLORS[$role]};
  }

  svg {
    color: ${({$role}) => STEP_COLORS[$role]};
    margin-block-start: 2px;
  }

  .number {
    color: var(--cds-text-secondary);
    font-size: var(--cds-label-01-font-size);
    font-variant-numeric: tabular-nums;
  }

  .text {
    display: flex;
    flex-direction: column;
    min-inline-size: 0;
    overflow-wrap: anywhere;
    font-size: var(--cds-body-compact-01-font-size);
  }

  .kind {
    color: ${({$role}) => STEP_COLORS[$role]};
    font-size: var(--cds-label-01-font-size);
  }

  .detail {
    color: var(--cds-text-secondary);
    font-size: var(--cds-label-01-font-size);
  }
`;

export {
  ContentsHeader,
  ContentsList,
  ContentsRail,
  ContentsStep,
  CompactionContainer,
  CompactionHeading,
  CompactionMessage,
  HistoryNavigation,
  ConversationContainer,
  Messages,
  StatusHint,
  LoopIterationMarker,
  ShowMoreButton,
};
