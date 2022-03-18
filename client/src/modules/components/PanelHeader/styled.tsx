/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const Container = styled.div`
  ${({theme}) => {
    const colors = theme.colors.decisionsList.header;

    return css`
      display: flex;
      min-height: 37px;
      align-items: center;
      background-color: ${colors.backgroundColor};
      padding: 8px 0 8px 19px;
      font-size: 16px;
      font-weight: 600;
      color: ${theme.colors.text01};
      border-bottom: solid 1px ${colors.borderColor};
    `;
  }}
`;

const InstancesCount = styled.span`
  ${({theme}) => {
    const colors = theme.colors.list.header.title;

    return css`
      border-left: 1px solid ${colors.borderColor};
      padding-left: 30px;
      font-size: 14px;
      font-weight: 500;
      margin-left: 34px;
    `;
  }}
`;

export {Container, InstancesCount};
