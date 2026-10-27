/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {styles} from '@carbon/elements';

const FilterSwitcher = styled.div`
  display: inline-flex;
  gap: var(--cds-spacing-01);
  padding: var(--cds-spacing-01);
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
    box-shadow: 0 1px 2px var(--cds-shadow);
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

export {FilterSwitcher, FilterSwitcherButton};
