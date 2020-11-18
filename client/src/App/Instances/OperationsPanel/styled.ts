/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

type OperationsListProps = {
  isInitialLoadComplete?: boolean;
};

const OperationsList = styled.ul<OperationsListProps>`
  ${({theme, isInitialLoadComplete}) => {
    const colors = theme.colors.operationsPanel.operationsList;

    return css`
      li:first-child {
        border-top: none;
      }

      li:last-child {
        border-bottom: ${!isInitialLoadComplete ? colors.borderColor : 'none'};
      }
    `;
  }}
`;

const EmptyMessage = styled.div`
  ${({theme}) => {
    const colors = theme.colors.operationsPanel.emptyMessage;

    return css`
      border: 1px solid ${colors.borderColor};
      border-radius: 3px;
      margin: 30px 17px 0 18px;
      padding: 29px 44px 29px 32px;
      text-align: center;
      font-size: 13px;
      font-family: IBM Plex Sans;
      color: ${colors.color};
      background-color: ${colors.backgroundColor};
    `;
  }}
`;

export {OperationsList, EmptyMessage};
