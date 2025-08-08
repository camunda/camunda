/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {Transition as TransitionComponent} from 'modules/components/Transition';

type TransitionProps = {
  timeout: number;
};

const Transition = styled(TransitionComponent)<TransitionProps>`
  ${({timeout}) => {
    return css`
      &.transition-enter {
        right: -100%;
      }
      &.transition-enter-active {
        right: 0%;
        transition: right ${timeout}ms;
      }
      &.transition-exit {
        right: 0%;
      }
      &.transition-exit-active {
        right: -100%;
        transition: right ${timeout}ms;
      }
    `;
  }}
`;

const FilterRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--cds-spacing-03) var(--cds-spacing-05);
  border-bottom: 1px solid var(--cds-border-subtle-01);
`;

const ResultsText = styled.div`
  font-family: 'IBM Plex Sans', sans-serif;
  font-weight: 600;
  font-size: 14px;
  line-height: 18px;
  color: var(--cds-text-primary);
`;

type FilterButtonProps = {
  $isActive: boolean;
};

const FilterButton = styled.button<FilterButtonProps>`
  display: inline-flex;
  align-items: center;
  gap: var(--cds-spacing-02);
  background: transparent;
  border: none;
  padding: var(--cds-spacing-02);
  cursor: pointer;
  font-family: inherit;
  font-size: 14px;
  color: var(--cds-link-primary);

  &:hover {
    text-decoration: underline;
  }

  svg {
    fill: ${({$isActive}) =>
      $isActive ? 'var(--cds-icon-interactive)' : 'var(--cds-icon-primary)'};
  }
`;

const ResetButton = styled.button`
  background: transparent;
  border: none;
  padding: var(--cds-spacing-02);
  cursor: pointer;
  font-family: inherit;
  font-size: 14px;
  color: var(--cds-link-primary);
  margin-left: var(--cds-spacing-03);

  &:hover {
    text-decoration: underline;
  }
`;

export {Transition, FilterRow, ResultsText, FilterButton, ResetButton};
