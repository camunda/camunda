/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";
import {
  CheckmarkFilled as BaseCheckmarkFilled,
  ErrorFilled as BaseErrorFilled,
} from "@carbon/react/icons";
import { Column, Grid as CarbonGrid } from "@carbon/react";
import { styles } from "@carbon/elements";

const OperationLogName = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
`;

const SuccessIcon = styled(BaseCheckmarkFilled)`
  fill: var(--cds-support-success);
`;

const ErrorIcon = styled(BaseErrorFilled)`
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

const DatePickerWrapper = styled.div`
  .cds--date-picker {
    width: 100%;
  }

  .cds--date-picker-container {
    width: 100%;
    max-width: 100%;
  }

  .cds--date-picker-input__wrapper {
    display: contents;
    width: 100%;
  }

  input.cds--date-picker__input {
    width: 100%;
    max-width: 100%;
  }
`;

const PropertyText = styled.div`
  ${styles.caption01}
`;

const OwnerInfo = styled.div`
  white-space: nowrap;
`;

export {
  OperationLogName,
  SuccessIcon,
  ErrorIcon,
  Grid,
  ColumnRightPadding,
  CenteredRow,
  DatePickerWrapper,
  PropertyText,
  OwnerInfo,
};
