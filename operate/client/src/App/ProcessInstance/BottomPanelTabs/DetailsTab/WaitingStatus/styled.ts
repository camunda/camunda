/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

const StatusContainer = styled.div`
  display: flex;
  flex-direction: column;
  gap: var(--cds-spacing-03);
  padding-top: var(--cds-spacing-05);
`;

const StatusHeading = styled.h5`
  font-size: var(--cds-heading-compact-01-font-size);
  font-weight: var(--cds-heading-compact-01-font-weight);
  line-height: var(--cds-heading-compact-01-line-height);
  margin: 0;
`;

const StatusItem = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-03);
  font-size: var(--cds-body-01-font-size);
  font-weight: var(--cds-body-01-font-weight);
  line-height: var(--cds-body-01-line-height);
  letter-spacing: var(--cds-body-01-letter-spacing);
`;

export {StatusContainer, StatusHeading, StatusItem};
