/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

// The styles use container queries, which do not support CSS variables in their selectors.
const EDITOR_BASIS = '400px';
const COLUMN_GAP = '1rem'; // --cds-spacing-05

const Description = styled.p`
  // Modal's set a default top margin for paragraph content.
  margin-block-start: 0 !important;
  color: var(--cds-text-secondary);
`;

const Columns = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${COLUMN_GAP};
  container-type: inline-size;
`;

const Column = styled.section`
  flex: 1 1 ${EDITOR_BASIS};
  display: grid;
  grid-template-rows: auto 1fr;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: var(--cds-spacing-03);

  & > :last-child {
    align-self: start;
    grid-column: 1 / -1;
  }
`;

const EditorContainer = styled.div`
  block-size: 40vh;

  @container (max-inline-size: calc(2 * ${EDITOR_BASIS} + ${COLUMN_GAP})) {
    block-size: 25vh;
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

export {Description, Columns, Column, ColumnLabel, EditorContainer, EmptyHint};
