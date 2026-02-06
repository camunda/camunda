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

const StatusColumnContainer = styled.div`
  table thead tr th:first-child,
  table tbody tr td:first-child {
    width: 40px;
    min-width: 40px;
    max-width: 40px;
    padding: 0 var(--cds-spacing-03);
  }
  table tbody tr td:first-child {
    display: flex;
    align-items: center;
    justify-content: center;
  }
`;

const OperationsLogContainer = styled.div`
  .cds--popover[role='tooltip'] .cds--popover-content {
    padding-top: var(--cds-spacing-02);
    padding-bottom: var(--cds-spacing-02);
  }
`;

export { OperationLogName, SuccessIcon, ErrorIcon, StatusColumnContainer, OperationsLogContainer };
