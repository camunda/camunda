/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {StructuredList as BaseStructuredList} from 'modules/components/StructuredList';
import {WarningFilled as BaseWarningFilled} from '@carbon/react/icons';
import {supportError, spacing01, spacing03} from '@carbon/elements';
import styled from 'styled-components';
import {Stack as BaseStack, Dropdown as BaseDropdown} from '@carbon/react';

const Content = styled.div`
  position: relative;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: var(--cds-spacing-03) var(--cds-spacing-05);

  .cds--loading-overlay {
    position: absolute;
  }
`;

const StructuredList = styled(BaseStructuredList)`
  [role='table'] {
    table-layout: fixed;
  }
`;

const CellContainer = styled(BaseStack)`
  padding: ${spacing03} ${spacing01};
`;

const WarningFilled = styled(BaseWarningFilled)`
  fill: ${supportError};
  margin-top: ${spacing01};
`;

const Dropdown = styled(BaseDropdown)`
  width: 200px;
`;

const EmptyMessageWrapper = styled.div`
  display: flex;
  height: 100%;
  justify-content: center;
  align-items: center;
  text-align: center;

  > div {
    max-width: 475px;
  }
`;

const dropdownHeight = '32px';

const Stack = styled(BaseStack)<{$isUserTask: boolean}>`
  height: ${({$isUserTask}) =>
    $isUserTask ? `calc(100% - ${dropdownHeight})` : '100%'};
`;

export {
  Content,
  StructuredList,
  CellContainer,
  WarningFilled,
  Dropdown,
  EmptyMessageWrapper,
  Stack,
};
