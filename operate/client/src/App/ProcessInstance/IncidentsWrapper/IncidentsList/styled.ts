/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {CodeSnippet} from '@carbon/react';
import {styles} from '@carbon/elements';

const ListContainer = styled.div`
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 var(--cds-spacing-05);
`;

const ExpandedPanel = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);
`;

const ErrorLabel = styled.span`
  ${styles.label01};
  color: var(--cds-text-secondary);
`;

const ErrorCodeSnippet = styled(CodeSnippet)`
  &&.cds--snippet--multi {
    background-color: var(--cds-layer-02);
  }
  && pre {
    padding-bottom: var(--cds-spacing-03);
  }
  && .cds--copy-btn {
    background-color: transparent;
    &:hover {
      background-color: var(--cds-layer-hover-02);
    }
  }
`;

const MetaRow = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
`;

const MetaLabel = styled.span`
  ${styles.label01};
  color: var(--cds-text-secondary);
`;

const MetaValue = styled.span`
  ${styles.bodyShort01};
  color: var(--cds-text-primary);
`;

const ActionsRow = styled.div`
  display: flex;
  justify-content: flex-start;
  gap: var(--cds-spacing-03);
  padding-top: var(--cds-spacing-02);
  border-top: 1px solid var(--cds-border-subtle-01);
  margin-top: var(--cds-spacing-01);
`;

export {
  ListContainer,
  ExpandedPanel,
  ErrorLabel,
  ErrorCodeSnippet,
  MetaRow,
  MetaLabel,
  MetaValue,
  ActionsRow,
};
