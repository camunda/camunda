/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Input as BasicInput} from 'modules/components/Input';
import {Warning as BasicWarning} from 'modules/components/Warning';
const Container = styled.div`
  position: relative;
  display: flex;
  justify-content: space-between;
`;

const TextInput = styled(BasicInput)`
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

const Warning = styled(BasicWarning)`
  position: absolute;
  right: -26px;
`;

export {Container, TextInput, Warning};
