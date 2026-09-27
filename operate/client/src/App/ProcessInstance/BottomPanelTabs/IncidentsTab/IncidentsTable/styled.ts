/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Layer} from '@carbon/react';

const ExpandedContent = styled(Layer)`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-05);
`;

const ExpandedField = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-02);
`;

const FieldLabel = styled.span`
  color: var(--cds-text-secondary);
`;

const ErrorMessageCell = styled.div`
  white-space: pre-wrap;
  word-break: break-word;
`;

const TruncatedText = styled.span`
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
`;

const FlexContainer = styled.div`
  display: flex;
  align-items: center;
`;

const ChildIncidentContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100%;
  padding: var(--cds-spacing-05);
  background-color: var(--cds-layer);
`;

export {
  ExpandedContent,
  ExpandedField,
  FieldLabel,
  ErrorMessageCell,
  TruncatedText,
  FlexContainer,
  ChildIncidentContainer,
};
