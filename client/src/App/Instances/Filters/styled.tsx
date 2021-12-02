/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import Panel from 'modules/components/Panel';
import {Input} from 'modules/components/Input';
import {ReactComponent as DefaultModal} from 'modules/components/Icon/modal.svg';
import IconButton from 'modules/components/IconButton';
import {CmText} from '@camunda-cloud/common-ui-react';

const FiltersForm = styled.form`
  width: 100%;
  height: 100%;
  flex-grow: 1;
  display: flex;
  flex-direction: column;
`;

const Row = styled.div`
  width: 100%;
  height: fit-content;
  padding-left: 20px;
  display: flex;

  &:not(:last-child) {
    padding-bottom: 20px;
  }

  &:first-child {
    padding-top: 20px;
  }

  & textarea,
  & input,
  & select {
    width: calc(100% - 20px);
  }
`;

const VariableRow = styled(Row)`
  display: flex;
  ${Input} {
    width: calc(50% - 22px);
  }

  ${Input}:focus {
    z-index: 1;
  }

  ${Input}:first-child:not(:focus) {
    border-right: none;
    border-top-right-radius: 0;
    border-bottom-right-radius: 0;
  }

  ${Input}:nth-child(2):not(:focus) {
    border-top-left-radius: 0;
    border-bottom-left-radius: 0;
  }
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

const JSONEditorButton = styled(IconButton)`
  svg {
    margin-top: 4px;
  }
`;

const ModalIcon = styled(DefaultModal)`
  ${({theme}) => {
    const colors = theme.colors.filtersPanel.modalIcon;

    return css`
      width: 16px;
      height: 16px;
      object-fit: contain;
      color: ${colors.color};
    `;
  }}
`;

const StatesHeader = styled(CmText)`
  display: block;
  margin-bottom: 8px;
`;

const InstanceStates = styled.div`
  padding-left: 20px;
`;

export {
  FiltersForm,
  Row,
  VariableRow,
  ResetButtonContainer,
  Fields,
  JSONEditorButton,
  ModalIcon,
  StatesHeader,
  InstanceStates,
};
