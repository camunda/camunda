/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

export const PageContainer = styled.div`
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
`;

export const PageHeader = styled.div`
  width: 100%;
  background-color: var(--cds-layer-01);
  border-bottom: 1px solid var(--cds-border-subtle-01);
  padding: var(--cds-spacing-04) var(--cds-spacing-05);
`;

export const PageWrapper = styled.div`
  display: flex;
  flex-direction: column;
  background-color: var(--cds-layer);
  min-height: 0;
  flex: 1;
`;

export const PanelHeader = styled.div`
  padding: var(--cds-spacing-05);
  padding-bottom: 0;

  h3 {
    margin-bottom: var(--cds-spacing-05);
  }
`;

export const TableContainer = styled.div`
  flex: 1;
  overflow: auto;
  padding: 0 var(--cds-spacing-05);

  thead {
    position: sticky;
    top: 0;
    z-index: 1;
    background-color: var(--cds-layer);
  }

  .cds--popover-content {
    padding: var(--cds-spacing-02) var(--cds-spacing-03);
    font-size: var(--cds-body-compact-01-font-size);
    max-width: 150px;
  }
`;

export const ItemGroup = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-04);
`;

export const Item = styled.span<{color?: string}>`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
  cursor: default;
  min-width: 3rem;
  color: ${({color}) => color ?? 'inherit'};
`;

export const BatchStateIndicatorContainer = styled.div`
  display: flex;

  svg {
    align-self: center;
    margin-inline-end: var(--cds-spacing-03);
  }
`;
