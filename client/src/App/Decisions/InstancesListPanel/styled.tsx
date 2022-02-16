/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {CmIcon} from '@camunda-cloud/common-ui-react';
import Table from 'modules/components/Table';

type ContainerProps = {
  overflow: 'auto' | 'hidden';
};
const Container = styled.div<ContainerProps>`
  ${({theme, overflow}) => {
    const colors = theme.colors.decisionsList;

    return css`
      overflow: ${overflow};
      border: 1px solid ${colors.borderColor};
      background-color: ${colors.backgroundColor};
    `;
  }}
`;

const Title = styled.div`
  ${({theme}) => {
    const colors = theme.colors.decisionsList.header;

    return css`
      background-color: ${colors.backgroundColor};
      padding: 8px 0 8px 19px;
      font-size: 16px;
      font-weight: 600;
      color: ${theme.colors.text01};
    `;
  }}
`;

const TD = styled(Table.TD)`
  ${({theme}) => {
    return css`
      color: ${theme.colors.text01};
    `;
  }}
`;

const Name = styled(TD)`
  display: flex;
  align-items: center;
`;

const State = styled(CmIcon)`
  margin-right: 10px;
  margin-left: 11px;
`;

const DecisionColumnHeader = styled.div`
  margin-left: 14px;
`;

const TH = styled(Table.TH)`
  ${({theme}) => {
    return css`
      font-weight: 500;
      white-space: nowrap;
      color: ${theme.colors.text01};
    `;
  }}
`;

const TR = styled(Table.TR)`
  line-height: 36px;
`;

export {Container, Title, Name, State, DecisionColumnHeader, TH, TD, TR};
