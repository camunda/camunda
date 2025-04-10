/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { helperText01, textSecondary } from "@carbon/elements";
import styled from "styled-components";

export const PrimaryText = styled.p`
  font-size: var(--cds-body-compact-02-font-size, 1rem) !important;
  font-weight: var(--cds-body-compact-02-font-weight, 400) !important;
  line-height: var(--cds-body-compact-02-line-height, 1.375) !important;
  letter-spacing: var(--cds-body-compact-02-letter-spacing, 0) !important;
`;

export const SecondaryText = styled.p`
  color: ${textSecondary};
`;

export const HelperText = styled(SecondaryText)`
  font-size: ${helperText01.fontSize} !important;
  font-weight: ${helperText01.fontSize} !important;
  line-height: ${helperText01.lineHeight} !important;
  letter-spacing: ${helperText01.letterSpacing} !important;
  padding: 0 !important;
`;
