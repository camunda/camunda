/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Colors} from 'modules/theme';
import BaseInput from 'modules/components/Input';

import {ReactComponent as Warning} from 'modules/components/Icon/warning-message-icon.svg';

export const InputContainer = styled.div`
  position: relative;
`;

export const Input = styled(BaseInput)`
  ${props =>
    props.isIncomplete && `border-color: ${Colors.incidentsAndErrors};`}

  &:focus {
    ${props =>
      props.isIncomplete &&
      `box-shadow: 0 0 0 4px #ffafaf, 0 0 0 1px 
    ${Colors.incidentsAndErrors};`}
  }
`;

export const WarningIcon = styled(Warning)`
  position: absolute;
  width: 16px;
  fill: ${Colors.incidentsAndErrors};
  top: 3px;
  right: -16px;
`;
