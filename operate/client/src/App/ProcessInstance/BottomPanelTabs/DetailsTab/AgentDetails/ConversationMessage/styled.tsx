/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

type ActorType = 'user' | 'assistant' | 'system';

const accentColorByActor: Record<ActorType, string> = {
  system: 'var(--cds-status-gray)',
  assistant: 'var(--cds-status-purple)',
  user: 'var(--cds-status-blue)',
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

const Message = styled.div`
  flex: 1;
  overflow-y: auto;
  white-space: pre-wrap;
  font-size: var(--cds-body-compact-01-font-size);
  font-weight: var(--cds-body-compact-01-font-weight);
  line-height: var(--cds-body-compact-01-line-height);
  letter-spacing: var(--cds-body-compact-01-letter-spacing);
  color: var(--cds-text-primary);
`;

export {Container, MessageBlock, ActorLabel, Message, MessageActions};
