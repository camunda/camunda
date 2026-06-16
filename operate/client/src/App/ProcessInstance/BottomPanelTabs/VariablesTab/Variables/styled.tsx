/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {StructuredList as BaseStructuredList} from 'modules/components/StructuredList';
import {styles} from '@carbon/elements';

const VariablesContent = styled.div`
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
`;

const VariableName = styled.div`
  ${styles.bodyShort01};
  margin: var(--cds-spacing-02) 0;
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
`;

const StructuredList = styled(BaseStructuredList)`
  padding: var(--cds-spacing-05);
  [role='table'] {
    table-layout: fixed;
  }
`;

const EmptyMessageWrapper = styled.div`
  display: flex;
  height: 100%;
  justify-content: center;
  align-items: center;
`;

const DimmableResults = styled.div<{$dimmed: boolean}>`
  height: 100%;
  transition: opacity 150ms ease;
  opacity: ${({$dimmed}) => ($dimmed ? 0.5 : 1)};
  pointer-events: ${({$dimmed}) => ($dimmed ? 'none' : 'auto')};
`;

const FilterSwitcherContainer = styled.div`
  padding: var(--cds-spacing-05) var(--cds-spacing-05) 0;
`;

const FilterSwitcher = styled.div`
  display: inline-flex;
  gap: 2px;
  padding: 2px;
  border-radius: 6px;
  background-color: var(--cds-layer-accent-01);
`;

const FilterSwitcherButton = styled.button`
  ${styles.label01};
  appearance: none;
  border: none;
  cursor: pointer;
  padding: var(--cds-spacing-02) var(--cds-spacing-04);
  border-radius: 4px;
  background-color: transparent;
  color: var(--cds-text-secondary);
  box-shadow: none;

  &[aria-pressed='true'] {
    background-color: var(--cds-layer-01);
    color: var(--cds-text-primary);
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.15);
  }

  &:hover {
    background-color: var(--cds-layer-hover-01);
  }

  &[aria-pressed='true']:hover {
    background-color: var(--cds-layer-01);
  }

  &:focus-visible {
    outline: 2px solid var(--cds-focus);
    outline-offset: -2px;
  }
`;

export {
  VariablesContent,
  VariableName,
  StructuredList,
  EmptyMessageWrapper,
  DimmableResults,
  FilterSwitcherContainer,
  FilterSwitcher,
  FilterSwitcherButton,
};
