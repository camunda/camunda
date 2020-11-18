/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const Select = styled.select`
  ${({theme}) => {
    const colors = theme.colors.modules.select;
    const shadows = theme.shadows.modules.select;

    return css`
      width: 100%;
      height: 26px;
      border: solid 1px ${theme.colors.ui05};
      border-radius: 3px;
      background-color: ${colors.default.backgroundColor};
      color: ${colors.default.color};
      font-family: IBM Plex Sans, Arial;
      font-size: 13px;
      box-shadow: ${shadows.box};

      &:disabled {
        background-color: ${colors.disabled.backgroundColor};
        border-color: ${colors.disabled.borderColor};
        color: ${colors.disabled.color};
        box-shadow: none;
        cursor: not-allowed;
      }

      /* removes default dotted-line-focus in firefox*/
      &:-moz-focusring {
        color: transparent;
        text-shadow: ${shadows.text};
      }
    `;
  }}
`;

export {Select};
