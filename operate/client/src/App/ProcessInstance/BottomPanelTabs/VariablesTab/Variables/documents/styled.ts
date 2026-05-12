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

const DocumentMeta = styled.span`
  ${styles.helperText01};
  color: var(--cds-text-secondary);
  white-space: nowrap;
  flex: 0 0 auto;
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
  flex-direction: column;
  gap: var(--cds-spacing-03);
  padding: var(--cds-spacing-03) var(--cds-spacing-05);
  background-color: var(--cds-layer);
`;

const ToolbarSearch = styled.div`
  width: 100%;

  /* Carbon's Search defaults to a transparent input with only a bottom border.
     The rest of Operate's filter inputs use the field token, so match that
     here for visual consistency. */
  .cds--search-input {
    background-color: var(--cds-field);
  }
`;

const FilterChipRow = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
  flex: 0 0 auto;

  /* Carbon's SelectableTag defaults to a button-secondary look (black when
     selected). Override to the softer cool-gray ramp from the Figma design. */
  .cds--tag--selectable {
    background-color: var(--cds-tag-background-cool-gray);
    color: var(--cds-tag-color-cool-gray);
    border: none;
  }

  .cds--tag--selectable:hover {
    background-color: var(--cds-tag-hover-cool-gray);
    color: var(--cds-tag-color-cool-gray);
  }

  .cds--tag--selectable[aria-pressed='true'] {
    background-color: var(--cds-layer-active-01);
    color: var(--cds-text-primary);
  }

  .cds--tag--selectable[aria-pressed='true']:hover {
    background-color: var(--cds-layer-hover-01);
  }
`;

export {
  DocumentRow,
  DocumentLabel,
  DocumentFileName,
  DocumentMeta,
  ListSummary,
  ModalListItem,
  ModalListItemLabel,
  ModalListItemFileName,
  ModalListItemMeta,
  PreviewIframeContainer,
  PreviewImageContainer,
  Toolbar,
  ToolbarSearch,
  FilterChipRow,
};
