/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';

const PageContainer = styled.div`
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: var(--cds-spacing-05);
  gap: var(--cds-spacing-05);
`;

const NotebookTitle = styled.h1`
  ${styles.productiveHeading04};
  color: var(--cds-text-primary);
`;

const WidgetsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--cds-spacing-05);
  flex: 1;
  overflow-y: auto;
`;

const PromptSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);
`;

const PromptRow = styled.div`
  display: flex;
  gap: var(--cds-spacing-03);
  align-items: flex-end;
`;

const WidgetTitle = styled.h4`
  ${styles.productiveHeading02};
  color: var(--cds-text-primary);
  margin: 0 0 var(--cds-spacing-04) 0;
`;

const WidgetTable = styled.table`
  width: 100%;
  border-collapse: collapse;
  ${styles.bodyShort01};

  th,
  td {
    text-align: left;
    padding: var(--cds-spacing-03) var(--cds-spacing-04);
    border-bottom: 1px solid var(--cds-border-subtle);
  }

  th {
    ${styles.productiveHeading01};
    color: var(--cds-text-secondary);
    background: var(--cds-layer-accent);
  }

  td {
    color: var(--cds-text-primary);
  }
`;

const EmptyState = styled.p`
  ${styles.bodyShort01};
  color: var(--cds-text-helper);
  margin: 0;
`;

export {
  PageContainer,
  NotebookTitle,
  WidgetsGrid,
  PromptSection,
  PromptRow,
  WidgetTitle,
  WidgetTable,
  EmptyState,
};
