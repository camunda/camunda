/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const LinkButton = styled.button`
  ${({theme, size}) => {
    return css`
      padding: 0;
      margin: 0;
      background: transparent;
      border: 0;
      font-size: ${size === 'small' ? 12 : 14}px;
      text-decoration: underline;
      color: ${theme.colors.linkDefault};

      &:hover {
        color: ${theme.colors.linkHover};
      }

      &:active {
        color: ${theme.colors.linkActive};
      }
    `;
  }}
`;

export {LinkButton};
