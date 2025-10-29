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

export const DocumentsContent = styled.div`
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
`;

export const DocumentName = styled.div`
  ${styles.bodyShort01};
  margin: var(--cds-spacing-02) 0;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
`;

export const DocumentInfo = styled.div`
  ${styles.bodyShort01};
  margin: var(--cds-spacing-02) 0;
`;

export const StructuredList = styled(BaseStructuredList)`
  padding: var(--cds-spacing-05);
  [role='table'] {
    table-layout: fixed;
  }
`;

export const ActionsContainer = styled.div`
  display: flex;
  justify-content: end;
  min-width: var(--cds-spacing-10);
  gap: var(--cds-spacing-03);
  align-items: center;
  
  .cds--tooltip-content {
    text-overflow: ellipsis;
    overflow: hidden;
    white-space: nowrap;
  }
`;

export const Spacer = styled.div`
  width: 2rem;
  height: 2rem;
`;
