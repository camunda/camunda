/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceHistoryRole} from '@camunda/camunda-api-zod-schemas/8.10';
import styled from 'styled-components';

type ActorType = AgentInstanceHistoryRole | 'SYSTEM';

const accentColorByActor: Record<ActorType, string> = {
  SYSTEM: 'var(--cds-status-gray)',
  ASSISTANT: 'var(--cds-status-purple)',
  USER: 'var(--cds-status-blue)',
  TOOL_RESULT: 'var(--cds-status-yellow)',
};

const Container = styled.div<{$actor: ActorType}>`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-02);
  padding: var(--cds-spacing-04);
  border-left: 3px solid ${({$actor}) => accentColorByActor[$actor]};
  background-color: var(--cds-layer-02);
  border-radius: 4px;
`;

const ActorLabel = styled.h5`
  font-size: var(--cds-label-01-font-size);
  font-weight: var(--cds-label-01-font-weight);
  line-height: var(--cds-label-01-line-height);
  letter-spacing: var(--cds-label-01-letter-spacing);
  color: var(--cds-text-secondary);
  text-transform: uppercase;
`;

const MessageActions = styled.div`
  display: flex;
  opacity: 0;

  &:has(:focus-visible) {
    opacity: 1;
  }
`;

const MessageBlock = styled.div`
  display: flex;
  gap: var(--cds-spacing-03);
  max-height: 160px;

  &:hover ${MessageActions} {
    opacity: 1;
  }
`;

const TextContent = styled.div`
  flex: 1;
  overflow-y: auto;
  font-size: var(--cds-body-compact-01-font-size);
  font-weight: var(--cds-body-compact-01-font-weight);
  line-height: var(--cds-body-compact-01-line-height);
  letter-spacing: var(--cds-body-compact-01-letter-spacing);
  color: var(--cds-text-primary);
`;

const ObjectContent = styled.pre`
  flex: 1;
  overflow: auto;
  border-radius: 4px;
  padding: var(--cds-spacing-03);
  background-color: var(--cds-layer-03);
  font-family: var(--cds-code-01-font-family);
  font-size: var(--cds-code-01-font-size);
  font-weight: var(--cds-code-01-font-weight);
  line-height: var(--cds-code-01-line-height);
  letter-spacing: var(--cds-code-01-letter-spacing);
  white-space: pre-wrap;
  word-break: break-word;
`;

export {
  Container,
  MessageBlock,
  ActorLabel,
  TextContent,
  ObjectContent,
  MessageActions,
};
