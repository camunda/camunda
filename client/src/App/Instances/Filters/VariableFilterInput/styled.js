/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors} from 'modules/theme';
import BasicTextInput from 'modules/components/Input';
import {ReactComponent as Warning} from 'modules/components/Icon/warning-message-icon.svg';

export const VariableFilterInput = styled.div`
  position: relative;
  display: flex;
  justify-content: space-between;

  ${props =>
    props.hasError &&
    `
    input {
      border-color: ${Colors.incidentsAndErrors};
    }
  `}
`;

export const TextInput = styled(BasicTextInput)`
  min-width: 0;
  &:first-child {
    border-top-right-radius: 0;
    border-bottom-right-radius: 0;
  }

  &:nth-child(2) {
    border-top-left-radius: 0;
    border-bottom-left-radius: 0;
    border-left: none;
  }

  :focus {
    z-index: 2;
  }
`;

export const WarningIcon = styled(Warning)`
  position: absolute;
  width: 16px;
  fill: ${Colors.incidentsAndErrors};
  top: 3px;
  right: -21px;
`;
