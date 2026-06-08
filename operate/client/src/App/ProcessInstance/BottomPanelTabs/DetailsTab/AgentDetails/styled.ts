/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const AgentDetailsContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);

  /* Small accordion overrides that apply to both Accordion and AccordionSkeleton. */
  .cds--accordion__item:first-child {
    border-block-start: none;
  }
  .cds--accordion__content {
    padding-inline-end: var(--cds-spacing-02);
  }
`;

const ErrorHint = styled.span`
  font-size: var(--cds-body-compact-01-font-size);
  line-height: var(--cds-body-compact-01-line-height);
  letter-spacing: var(--cds-body-compact-01-letter-spacing);
  color: var(--cds-text-primary);
`;

const AgentHeading = styled.h5`
  font-size: var(--cds-heading-compact-01-font-size);
  font-weight: var(--cds-heading-compact-01-font-weight);
  line-height: var(--cds-heading-compact-01-line-height);
  margin: 0;
`;

const MetricsRow = styled.div`
  display: flex;
  gap: var(--cds-spacing-05);
  flex-wrap: wrap;
`;

const ModelInfo = styled.p`
  font-size: var(--cds-body-compact-01-font-size);
  line-height: var(--cds-body-compact-01-line-height);
  letter-spacing: var(--cds-body-compact-01-letter-spacing);
  color: var(--cds-text-secondary);
`;

const ModelInfoLabel = styled.strong`
  font-weight: var(--cds-heading-compact-01-font-weight);
  color: var(--cds-text-primary);
`;

export {
  AgentDetailsContainer,
  AgentHeading,
  ErrorHint,
  MetricsRow,
  ModelInfo,
  ModelInfoLabel,
};
