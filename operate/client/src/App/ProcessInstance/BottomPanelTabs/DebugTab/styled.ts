/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {WarningFilled as BaseWarningFilled} from '@carbon/react/icons';
import {supportWarning, supportSuccess, supportError} from '@carbon/elements';

const Content = styled.div`
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-04);
  padding: 0 var(--cds-spacing-05);
  overflow: hidden;
`;

const ExpressionLabel = styled.label`
  flex: 0 0 auto;
  font-size: var(--cds-label-01-font-size);
  font-weight: var(--cds-label-01-font-weight);
  line-height: var(--cds-label-01-line-height);
  letter-spacing: var(--cds-label-01-letter-spacing);
  color: var(--cds-text-secondary);
`;

const ExpressionEditor = styled.div`
  flex: 0 0 auto;

  .cm-editor {
    background-color: transparent;
    color: var(--cds-text-primary);
  }


`;

const ContextHint = styled.div`
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
  font-size: var(--cds-helper-text-01-font-size);
  color: var(--cds-text-secondary);
`;

const WarningFilled = styled(BaseWarningFilled)`
  fill: ${supportWarning};
  flex-shrink: 0;
`;

const ResultContainer = styled.div`
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  > [role='tabpanel'] {
    flex: 1 1 auto;
    min-height: 0;
    padding: var(--cds-spacing-04) 0;
    overflow: auto;
  }
`;

const Status = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
  margin-bottom: var(--cds-spacing-04);
  font-weight: 600;
`;

const StatusIconSuccess = styled.span`
  display: inline-flex;
  color: ${supportSuccess};
`;

const StatusIconWarning = styled.span`
  display: inline-flex;
  color: ${supportWarning};
`;

const StatusIconError = styled.span`
  display: inline-flex;
  color: ${supportError};
`;

const ResultValue = styled.pre`
  margin: 0;
  padding: var(--cds-spacing-04);
  background-color: var(--cds-layer-accent);
  font-family: var(--cds-code-01-font-family);
  font-size: var(--cds-code-01-font-size);
  white-space: pre-wrap;
  word-break: break-word;
`;

const RawJson = styled.pre`
  margin: 0;
  padding: var(--cds-spacing-04);
  background-color: var(--cds-layer-accent);
  font-family: var(--cds-code-01-font-family);
  font-size: var(--cds-code-01-font-size);
  white-space: pre-wrap;
  word-break: break-word;
`;

const WarningList = styled.div`
  margin-top: var(--cds-spacing-05);
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-02);
`;

const WarningListTitle = styled.div`
  font-weight: 600;
  margin-bottom: var(--cds-spacing-02);
`;

const WarningItem = styled.div`
  display: flex;
  align-items: flex-start;
  gap: var(--cds-spacing-02);
  color: var(--cds-text-primary);

  > svg {
    flex-shrink: 0;
    color: ${supportWarning};
    margin-top: 2px;
  }
`;

export {
  Content,
  ExpressionLabel,
  ExpressionEditor,
  ContextHint,
  WarningFilled,
  ResultContainer,
  Status,
  StatusIconSuccess,
  StatusIconWarning,
  StatusIconError,
  ResultValue,
  RawJson,
  WarningList,
  WarningListTitle,
  WarningItem,
};
