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

const TypeStrip = styled.div`
  ${styles.bodyShort01};
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
  flex: 0 0 auto;
`;

const TypeStripButton = styled.button<{$active: boolean}>`
  appearance: none;
  border: none;
  background: transparent;
  padding: var(--cds-spacing-01) var(--cds-spacing-02);
  cursor: pointer;
  color: ${({$active}) =>
    $active ? 'var(--cds-text-primary)' : 'var(--cds-text-secondary)'};
  font-weight: ${({$active}) => ($active ? 600 : 400)};
  border-radius: 2px;

  &:hover {
    color: var(--cds-text-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--cds-focus);
    outline-offset: 1px;
  }
`;

const TypeStripDivider = styled.span`
  color: var(--cds-text-placeholder);
  user-select: none;
`;

export {
  DocumentRow,
  DocumentLabel,
  DocumentFileName,
  ListSummary,
  ModalListItem,
  ModalListItemLabel,
  ModalListItemFileName,
  ModalListItemMeta,
  PreviewIframeContainer,
  PreviewImageContainer,
  Toolbar,
  ToolbarSearch,
  TypeStrip,
  TypeStripButton,
  TypeStripDivider,
};
