/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";

export const RightAlignedButtonSet = styled.div`
  display: flex;
  justify-content: flex-end;
  width: 100%;
  gap: var(--cds-spacing-06);
`;

export const InfoHint = styled.div`
  display: flex;
  align-items: flex-start;
  gap: var(--cds-spacing-03);
  margin-top: var(--cds-spacing-03);
  color: var(--cds-text-primary);
  font-size: var(--cds-helper-text-01-font-size, 0.75rem);
  line-height: var(--cds-helper-text-01-line-height, 1.34);
`;
