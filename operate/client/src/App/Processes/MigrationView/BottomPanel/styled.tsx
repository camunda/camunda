/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {spacing03, supportSuccess, supportWarning} from '@carbon/elements';
import {
  CheckmarkFilled as BaseCheckmark,
  ArrowRight as BaseArrowRight,
  WarningFilled as BaseWarningFilled,
} from '@carbon/react/icons';
import {DataTable as BaseDataTable} from 'modules/components/DataTable';
import {Select as BaseSelect} from '@carbon/react';

const BottomSection = styled.section`
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--cds-layer);
  overflow: auto;
  position: relative;
`;

// ToggleContainer is positioned absolutely related to BottomSection
const ToggleContainer = styled.div`
  position: absolute;
  right: 50%;
  top: 9px;
  z-index: 1;
  padding-right: var(--cds-spacing-05);
`;

const LeftColumn = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--cds-text-primary);
`;

const SourceElementName = styled.div`
  flex-grow: 1;
`;

const ArrowRight = styled(BaseArrowRight)`
  margin-left: var(--cds-spacing-06);
`;

const DataTable = styled(BaseDataTable)`
  td {
    padding-top: 0;
    padding-bottom: 0;
  }
`;

const ErrorMessageContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
`;

const WarningFilled = styled(BaseWarningFilled)`
  fill: ${supportWarning};
  margin-right: ${spacing03};

  [data-icon-path='inner-path'] {
    opacity: 1;
    fill: black;
  }
`;

const CheckmarkFilled = styled(BaseCheckmark)`
  color: ${supportSuccess};
`;

const Select = styled(BaseSelect)`
  width: 288px;
`;

const IconContainer = styled.div`
  > svg {
    block-size: 100%;
  }
`;

export {
  BottomSection,
  LeftColumn,
  SourceElementName,
  ArrowRight,
  DataTable,
  ErrorMessageContainer,
  CheckmarkFilled,
  Select,
  IconContainer,
  ToggleContainer,
  WarningFilled,
};
