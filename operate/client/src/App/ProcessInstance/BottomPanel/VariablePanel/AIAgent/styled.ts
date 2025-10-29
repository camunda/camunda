/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {StructuredList as BaseStructuredList} from 'modules/components/StructuredList';
import {styles} from '@carbon/elements';

export const AIAgentContent = styled.div`
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
`;

export const MessageInfo = styled.div`
  ${styles.bodyShort01};
  margin: var(--cds-spacing-02) 0;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
`;

export const MessageContent = styled.div`
  ${styles.bodyShort01};
  margin: var(--cds-spacing-02) 0;
`;

export const StructuredList = styled(BaseStructuredList)`
  padding: var(--cds-spacing-05);
  [role='table'] {
    table-layout: fixed;
  }
`;

export const AIAgentActions = styled.div`
  margin-top: auto;
  border-top: 1px solid var(--cds-border-subtle-01);
  display: flex;
  gap: var(--cds-spacing-03);
  padding: var(--cds-spacing-05);
`;
