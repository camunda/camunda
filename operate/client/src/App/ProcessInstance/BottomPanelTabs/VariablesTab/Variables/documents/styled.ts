/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';

const DocumentRow = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
  width: 100%;
  min-height: 2rem;
`;

const DocumentLabel = styled.div`
  ${styles.bodyShort01};
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
  flex: 1 1 auto;
  min-width: 0;
`;

const DocumentFileName = styled.span`
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
`;

const EmbeddedSummary = styled.div`
  ${styles.bodyShort01};
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
  min-height: 2rem;
  margin-bottom: var(--cds-spacing-03);
  color: var(--cds-text-secondary);
`;

const EmbeddedContainer = styled.div`
  width: 100%;
`;

/* Operations cell layout for the embedded (Case B) variable: top row aligns
   with EmbeddedSummary, bottom row aligns with the JSON viewer below it. */
const EmbeddedOps = styled.div`
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
`;

const EmbeddedOpsTopRow = styled.div`
  display: flex;
  justify-content: flex-end;
  align-items: center;
  min-height: 2rem;
  margin-bottom: var(--cds-spacing-03);
`;

const EmbeddedOpsBottomRow = styled.div`
  display: flex;
  justify-content: flex-end;
  align-items: flex-start;
  flex: 1 1 auto;
`;

const ListSummary = styled.div`
  ${styles.bodyShort01};
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
`;

const ModalListItem = styled.div<{$lastItem?: boolean}>`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--cds-spacing-04) 0;
  ${({$lastItem}) =>
    !$lastItem &&
    css`
      border-bottom: 1px solid var(--cds-border-subtle);
    `}
`;

const ModalListItemLabel = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-01);
  min-width: 0;
`;

const ModalListItemFileName = styled.span`
  ${styles.bodyShort01};
  font-weight: 500;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
`;

const ModalListItemMeta = styled.span`
  ${styles.helperText01};
  color: var(--cds-text-secondary);
`;

const PreviewIframeContainer = styled.div`
  height: 70vh;
  width: 100%;

  iframe {
    width: 100%;
    height: 100%;
    border: none;
  }
`;

const PreviewImageContainer = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  height: 70vh;

  img {
    max-width: 100%;
    max-height: 100%;
  }
`;

const Toolbar = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-04);
  padding: var(--cds-spacing-03) var(--cds-spacing-05);
  border-bottom: 1px solid var(--cds-border-subtle);
  background-color: var(--cds-layer);
  flex-wrap: nowrap;
`;

const ToolbarSearch = styled.div`
  flex: 1 1 auto;
  min-width: 0;
`;

const ToolbarTypeFilter = styled.div`
  flex: 0 0 auto;
  width: 220px;

  /* Carbon's ContentSwitcher distributes children with flex; pin them to
     equal width so "All" and "Documents" share the row evenly. */
  .cds--content-switcher button {
    flex: 1 1 0;
    min-width: 0;
  }
`;

export {
  DocumentRow,
  DocumentLabel,
  DocumentFileName,
  EmbeddedSummary,
  EmbeddedContainer,
  EmbeddedOps,
  EmbeddedOpsTopRow,
  EmbeddedOpsBottomRow,
  ListSummary,
  ModalListItem,
  ModalListItemLabel,
  ModalListItemFileName,
  ModalListItemMeta,
  PreviewIframeContainer,
  PreviewImageContainer,
  Toolbar,
  ToolbarSearch,
  ToolbarTypeFilter,
};
