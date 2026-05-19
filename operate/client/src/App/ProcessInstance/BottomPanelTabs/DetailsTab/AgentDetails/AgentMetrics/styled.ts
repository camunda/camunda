/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const MetricCardContainer = styled.article`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);
  padding: var(--cds-spacing-05);
  background-color: var(--cds-layer-02);
  min-width: 200px;
  border-radius: 4px;
  flex: 1;
`;

const MetricCardTitle = styled.span`
  font-size: var(--cds-label-01-font-size);
  line-height: var(--cds-label-01-line-height);
  letter-spacing: var(--cds-label-01-letter-spacing);
  font-weight: 600;
  text-transform: uppercase;
  color: var(--cds-text-secondary);
`;

const MetricCardValue = styled.span`
  font-size: var(--cds-productive-heading-03-font-size);
  font-weight: var(--cds-productive-heading-03-font-weight);
  line-height: var(--cds-productive-heading-03-line-height);
  color: var(--cds-text-primary);
`;

const MetricHelperText = styled.span`
  font-size: var(--cds-helper-text-01-font-size);
  line-height: var(--cds-helper-text-01-line-height);
  letter-spacing: var(--cds-helper-text-01-letter-spacing);
  color: var(--cds-text-secondary);
`;

const ProgressBar = styled.div<{$percent: number}>`
  background-color: var(--cds-border-subtle-01);
  width: 100%;
  height: 4px;
  border-radius: 2px;
  overflow: hidden;

  &::after {
    content: '';
    display: block;
    height: 100%;
    width: ${({$percent}) => $percent}%;
    background-color: var(--cds-link-primary);
    border-radius: 2px;
  }
`;

const TokenBreakdownContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-02);
`;

const TokenBreakdown = styled(MetricHelperText)<{$dotColor: string}>`
  display: inline-flex;
  align-items: center;
  gap: var(--cds-spacing-03);

  &::before {
    content: '';
    display: inline-block;
    width: 0.75em;
    height: 0.75em;
    border-radius: 50%;
    background-color: ${({$dotColor}) => $dotColor};
  }

  & > :last-child {
    color: var(--cds-text-primary);
    font-variant-numeric: tabular-nums;
  }
`;

export {
  MetricCardContainer,
  MetricCardTitle,
  MetricCardValue,
  MetricHelperText,
  ProgressBar,
  TokenBreakdownContainer,
  TokenBreakdown,
};
