/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const Container = styled.div<{$centered?: boolean}>`
  display: inline-flex;
  align-items: center;
  /* When anchored at the element's horizontal center, shift left by half the
     label width so it stays centered regardless of the text length. */
  ${({$centered}) => $centered && 'transform: translateX(-50%);'}
  font-size: var(--cds-label-01-font-size);
  font-weight: var(--cds-heading-compact-01-font-weight);
  /* equal-width digits so counts of the same length render the same width */
  font-variant-numeric: tabular-nums;
  line-height: var(--cds-label-01-line-height);
  letter-spacing: var(--cds-label-01-letter-spacing);
  border-radius: 11px;
  background-color: var(--cds-support-warning);
  color: #000000;
  padding: var(--cds-spacing-02) var(--cds-spacing-04);
  white-space: nowrap;
`;

export {Container};
