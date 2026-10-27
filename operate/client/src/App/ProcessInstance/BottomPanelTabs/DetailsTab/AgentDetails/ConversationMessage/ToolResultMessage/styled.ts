/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {
  ActorLabel,
  Container as MessageContainer,
  MessageHeader,
} from '../styled';

const ToolActions = styled.div`
  grid-area: actions;
  display: flex;
  opacity: 0;

  &:has(:focus-visible) {
    opacity: 1;
  }
`;

const Container = styled(MessageContainer)`
  display: grid;
  align-items: start;
  column-gap: var(--cds-spacing-03);
  grid-template-columns: 1fr auto;
  grid-template-areas:
    'header actions'
    'content actions';

  &:hover ${ToolActions} {
    opacity: 1;
  }
`;

const ToolHeader = styled(MessageHeader)`
  grid-area: header;

  & > svg {
    color: var(--cds-icon-secondary);
  }
`;

const ToolLabel = styled(ActorLabel)`
  text-transform: initial;
`;

const ResultPreview = styled.p`
  grid-area: content;
  font-family: var(--cds-code-01-font-family);
  font-size: var(--cds-code-01-font-size);
  font-weight: var(--cds-code-01-font-weight);
  line-height: var(--cds-code-01-line-height);
  letter-spacing: var(--cds-code-01-letter-spacing);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--cds-text-secondary);
  max-inline-size: 100ch;
`;

export {Container, ToolHeader, ToolLabel, ToolActions, ResultPreview};
