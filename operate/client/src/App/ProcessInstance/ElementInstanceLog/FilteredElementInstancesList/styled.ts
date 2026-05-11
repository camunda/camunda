/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';

const ScrollContainer = styled.div`
  overflow-y: auto;
  flex: 1;
  min-height: 0;
`;

const List = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0;
`;

const RowButton = styled.button<{$selected: boolean}>`
  display: block;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  padding: 0;
  color: inherit;

  &:hover {
    background-color: var(--cds-layer-hover-01);
  }

  &:focus-visible {
    outline: 2px solid var(--cds-focus);
    outline-offset: -2px;
  }

  ${({$selected}) =>
    $selected &&
    css`
      background-color: var(--cds-layer-selected-01);

      &:hover {
        background-color: var(--cds-layer-selected-hover-01);
      }
    `}
`;

const StatusRegion = styled.output`
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
`;

const EmptyStateContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: var(--cds-spacing-06);
`;

export {ScrollContainer, List, RowButton, StatusRegion, EmptyStateContainer};
