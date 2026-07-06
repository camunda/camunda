/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const Description = styled.p`
  // Modal's set a default top margin for paragraph content.
  margin-block-start: 0 !important;
  color: var(--cds-text-secondary);
`;

const Columns = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: var(--cds-spacing-05);
`;

const Column = styled.section`
  flex: 1 1 400px;
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

export {Description, Columns, Column, ColumnLabel, EmptyHint};
