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
`;

const AgentHeading = styled.h5`
  font-size: var(--cds-heading-compact-01-font-size);
  font-weight: var(--cds-heading-compact-01-font-weight);
  line-height: var(--cds-heading-compact-01-line-height);
  margin: 0;
`;

const StatusRow = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
  padding: var(--cds-spacing-03) 0;
`;

const StatusIconWrapper = styled.span`
  display: flex;
  align-items: center;
  color: var(--cds-icon-primary);
`;

const StatusLabel = styled.span`
  font-size: var(--cds-body-compact-01-font-size);
`;

export {
  AgentDetailsContainer,
  AgentHeading,
  StatusRow,
  StatusIconWrapper,
  StatusLabel,
};
