/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const EmptyMessage = styled.div`
  ${({theme}) => {
    const colors = theme.colors.emptyMessage;

    return css`
      padding-top: 40px;
      font-family: IBM Plex Sans;
      font-size: 16px;
      font-weight: 500;
      text-align: center;
      line-height: 20px;
      color: ${colors.color};

      span {
        display: block;
      }
    `;
  }}
`;

export {EmptyMessage};
