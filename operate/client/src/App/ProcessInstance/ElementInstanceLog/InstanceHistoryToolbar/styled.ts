/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';

const Toolbar = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);
  padding: var(--cds-spacing-03) var(--cds-spacing-05);
  background-color: var(--cds-layer);
`;

const ToolbarTopRow = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-04);
`;

const ToolbarSearch = styled.div`
  flex: 1 1 auto;
  min-width: 0;

  .cds--search-input {
    background-color: var(--cds-field);
  }
`;

const SortButton = styled.button`
  ${styles.label01};
  appearance: none;
  border: none;
  background: transparent;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: var(--cds-spacing-02);
  padding: var(--cds-spacing-01) var(--cds-spacing-02);
  color: var(--cds-text-primary);
  white-space: nowrap;
  flex: 0 0 auto;

  &:hover {
    color: var(--cds-text-primary);
    background-color: var(--cds-layer-hover-01);
  }

  &:focus-visible {
    outline: 2px solid var(--cds-focus);
    outline-offset: 1px;
  }
`;

const ChipRow = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
  flex-wrap: wrap;

  /* Carbon's SelectableTag defaults to a button-secondary look (black when
     selected). Override: unselected = transparent, selected = soft gray fill. */
  .cds--tag--selectable {
    background-color: transparent;
    color: var(--cds-tag-color-cool-gray);
    border: none;
  }

  .cds--tag--selectable:hover {
    background-color: var(--cds-layer-hover-01);
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

export {Toolbar, ToolbarTopRow, ToolbarSearch, SortButton, ChipRow};
