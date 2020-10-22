/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import BasicTextInput from 'modules/components/Input';
import {ReactComponent as Warning} from 'modules/components/Icon/warning-message-icon.svg';

const VariableFilterInput = styled.div`
  position: relative;
  display: flex;
  justify-content: space-between;
`;

const TextInput = styled(BasicTextInput)`
  ${({hasError}) => {
    return css`
      min-width: 0;
      &:first-child {
        border-top-right-radius: 0;
        border-bottom-right-radius: 0;
      }

      &:nth-child(2) {
        border-top-left-radius: 0;
        border-bottom-left-radius: 0;
        margin-left: -1px;
      }

      & :focus {
        z-index: 3;
      }

      ${hasError
        ? css`
            z-index: 2;
          `
        : ''}
    `;
  }}
`;

const WarningIcon = styled(Warning)`
  ${({theme}) => {
    return css`
      position: absolute;
      width: 16px;
      fill: ${theme.colors.incidentsAndErrors};
      top: 3px;
      right: -21px;
    `;
  }}
`;

export {VariableFilterInput, TextInput, WarningIcon};
