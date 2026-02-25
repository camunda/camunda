/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";
import {
  CheckmarkOutline as BaseCheckmarkOutline,
  ErrorOutline as BaseErrorOutline,
} from "@carbon/react/icons";
import { Column, Grid as CarbonGrid, Section } from "@carbon/react";
import { styles } from "@carbon/elements";

const OperationLogName = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
`;

const SuccessIcon = styled(BaseCheckmarkOutline)`
  fill: var(--cds-support-success);
`;

const ErrorIcon = styled(BaseErrorOutline)`
  fill: var(--cds-support-error);
`;

const Grid = styled(CarbonGrid)`
  padding: 0;
`;

const ColumnRightPadding = styled(Column)`
  margin-right: var(--cds-spacing-05);
`;

const CenteredRow = styled.div`
  display: flex;
  justify-content: center;
  width: 100%;
`;

const PropertyText = styled.div`
  ${styles.caption01}
`;

const OwnerInfo = styled.div`
  white-space: nowrap;
`;

const StickySection = styled(Section)`
  position: sticky;
  top: 0;
`;

export {
  OperationLogName,
  SuccessIcon,
  ErrorIcon,
  Grid,
  ColumnRightPadding,
  CenteredRow,
  PropertyText,
  OwnerInfo,
  StickySection,
};
