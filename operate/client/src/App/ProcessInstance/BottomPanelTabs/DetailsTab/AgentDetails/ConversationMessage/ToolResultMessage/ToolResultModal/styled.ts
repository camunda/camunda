/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Modal} from '@carbon/react';

const EDITOR_BASIS = '400px';
const COLUMN_GAP = '1rem'; // --cds-spacing-05

const Description = styled.p`
  // Modals set a default top margin for paragraph content.
  margin-block-start: 0 !important;
  color: var(--cds-text-secondary);
`;

const Columns = styled.div`
  display: grid;
  grid-template-columns: repeat(
    auto-fit,
    minmax(min(100%, ${EDITOR_BASIS}), 1fr)
  );
  grid-auto-rows: minmax(12rem, 1fr);
  gap: ${COLUMN_GAP};
  min-block-size: 0;
  overflow: auto;
`;

const Column = styled.section`
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: var(--cds-spacing-03);
  min-block-size: 0;
  min-inline-size: 0;

  & > :last-child {
    align-self: start;
    grid-column: 1 / -1;
  }
`;

const ResultContainer = styled.div`
  block-size: 100%;
  min-block-size: 0;
  overflow: auto;
  font-size: var(--cds-body-compact-01-font-size);
  line-height: var(--cds-body-compact-01-line-height);
  color: var(--cds-text-primary);
`;

const ToolModal = styled(Modal)`
  .cds--modal-container {
    block-size: 84vh;
    max-block-size: 84vh;
  }

  .cds--modal-content {
    display: flex;
    flex-direction: column;
    min-block-size: 0;
  }

  .cds--modal-content > ${Columns} {
    flex: 1 1 0;
  }
`;

const ColumnLabel = styled.h3`
  font-size: var(--cds-heading-compact-01-font-size);
  font-weight: var(--cds-heading-compact-01-font-weight);
  line-height: var(--cds-heading-compact-01-line-height);
  letter-spacing: var(--cds-heading-compact-01-letter-spacing);
  color: var(--cds-text-primary);
`;

const EmptyHint = styled.span`
  font-size: var(--cds-body-compact-01-font-size);
  line-height: var(--cds-body-compact-01-line-height);
  letter-spacing: var(--cds-body-compact-01-letter-spacing);
  font-weight: var(--cds-body-compact-01-font-weight);
  color: var(--cds-text-secondary);
`;

export {
  Description,
  Columns,
  Column,
  ColumnLabel,
  ResultContainer,
  EmptyHint,
  ToolModal,
};
