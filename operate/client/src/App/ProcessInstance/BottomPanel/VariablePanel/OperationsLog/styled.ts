/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {
  CheckmarkFilled as BaseCheckmarkFilled,
  ErrorFilled as BaseErrorFilled,
} from '@carbon/react/icons';
import {StructuredListCell, StructuredListRow} from '@carbon/react';

const Container = styled.section`
  height: 100%;
  display: flex;
  flex-direction: column;
`;

const OperationLogName = styled.div`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
`;

const CheckmarkFilled = styled(BaseCheckmarkFilled)`
  fill: var(--cds-support-success);
`;

const ErrorFilled = styled(BaseErrorFilled)`
  fill: var(--cds-support-error);
`;

const FirstColumn = styled(StructuredListCell)`
  min-width: 200px;
`;

const VerticallyAlignedRow = styled(StructuredListRow)`
  .cds--structured-list-td {
    vertical-align: middle;
  }
`;

const ParagraphWithIcon = styled.p`
  display: flex;
  align-items: center;
  gap: var(--cds-spacing-02);
`;

export {
  Container,
  OperationLogName,
  CheckmarkFilled,
  ErrorFilled,
  FirstColumn,
  VerticallyAlignedRow,
  ParagraphWithIcon,
};
