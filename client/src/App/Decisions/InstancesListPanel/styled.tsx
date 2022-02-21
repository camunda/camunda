/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import Table from 'modules/components/Table';
import StateIcon from 'modules/components/StateIcon';

const Container = styled.div`
  ${({theme}) => {
    const colors = theme.colors.decisionsList;
    return css`
      border: 1px solid ${colors.borderColor};
      background-color: ${colors.backgroundColor};
      display: flex;
      flex-direction: column;
      flex-grow: 1;
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

const State = styled(StateIcon)`
  margin-right: 10px;
  margin-left: 11px;
  top: 0;
  position: static;
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
      box-shadow: inset 0 -1px 0 ${theme.colors.decisionsList.header.th.borderColor};
    `;
  }}
`;

const TR = styled(Table.TR)`
  line-height: 36px;
  &:first-child {
    border-top-style: hidden;
  }
`;

const TRHeader = styled(Table.TR)`
  border-top: none;
`;

const List = styled.div`
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
`;

type ScrollableContentProps = {
  overflow: 'auto' | 'hidden';
};

const ScrollableContent = styled.div<ScrollableContentProps>`
  ${({overflow}) => {
    return css`
      width: 100%;
      height: 100%;
      overflow-y: ${overflow};
      flex: 1 0 0;
    `;
  }}
`;

const THead = styled(Table.THead)`
  position: sticky;
  top: 0;
`;

export {
  Container,
  Name,
  State,
  DecisionColumnHeader,
  TH,
  TD,
  TR,
  List,
  ScrollableContent,
  THead,
  TRHeader,
};
