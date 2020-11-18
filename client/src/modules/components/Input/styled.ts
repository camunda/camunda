/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

import {errorBorders} from 'modules/theme/interactions';

const Input = styled.input`
  ${({theme}) => {
    const colors = theme.colors.modules.input;

    return css`
      ${errorBorders};
      &::placeholder {
        color: ${colors.placeholder.color};
        font-style: italic;
      }

      font-family: IBM Plex Sans;
      font-size: 13px;
      height: 26px;
      width: 100%;
      padding: 4px 11px 5px 8px;
      border: solid 1px ${theme.colors.ui05};
      border-radius: 3px;
      background-color: ${colors.backgroundColor};
      color: ${colors.color};
    `;
  }}
`;

export {Input};
