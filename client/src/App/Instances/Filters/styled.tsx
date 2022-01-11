/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import Panel from 'modules/components/Panel';
import {CmText, CmDropdown} from '@camunda-cloud/common-ui-react';

const FiltersForm = styled.form`
  width: 100%;
  height: 100%;
  flex-grow: 1;
  display: flex;
  flex-direction: column;
`;

const OrderedFilters = styled.div`
  display: flex;
  flex-direction: column-reverse;
`;

const ResetButtonContainer = styled(Panel.Footer)`
  ${({theme}) => {
    const shadow = theme.shadows.filters.resetButtonContainer;

    return css`
      display: flex;
      justify-content: center;
      width: 100%;
      box-shadow: ${shadow};
      border-radius: 0;
    `;
  }}
`;

const Fields = styled.div`
  overflow-y: auto;
  flex-grow: 1;
`;

const ProcessHeader = styled(CmText)`
  display: block;
  padding: 24px 0 8px 20px;
`;

const StatesHeader = styled(CmText)`
  display: block;
  margin-bottom: 8px;
`;

const InstanceStates = styled.div`
  padding-left: 20px;
`;

const MoreFiltersDropdown = styled(CmDropdown)`
  display: flex;
  justify-content: flex-end;
`;

export {
  FiltersForm,
  ResetButtonContainer,
  Fields,
  ProcessHeader,
  StatesHeader,
  InstanceStates,
  OrderedFilters,
  MoreFiltersDropdown,
};
