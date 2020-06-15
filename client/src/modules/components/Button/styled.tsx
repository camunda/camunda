/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';

const Button = styled.button`
  height: 22px;
  padding: 0 11px;
  border-radius: 11px;
  border: solid 1px ${({theme}) => theme.colors.button.default.borderColor};
  background-color: ${({theme}) => theme.colors.button.default.backgroundColor};

  font-weight: 600;
  font-size: 13px;
  color: ${({theme}) => theme.colors.text.black};

  &::-moz-focus-inner {
    border: 0;
  }

  &:hover {
    background-color: ${({theme}) => theme.colors.button.hover.backgroundColor};
    border-color: ${({theme}) => theme.colors.button.hover.borderColor};
  }

  &:active {
    background-color: ${({theme}) =>
      theme.colors.button.active.backgroundColor};
    border-color: ${({theme}) => theme.colors.button.active.borderColor};
  }

  &:disabled {
    background-color: ${({theme}) =>
      theme.colors.button.disabled.backgroundColor};
    border-color: ${({theme}) => theme.colors.button.disabled.borderColor};
    cursor: not-allowed;
  }
`;

export {Button};
