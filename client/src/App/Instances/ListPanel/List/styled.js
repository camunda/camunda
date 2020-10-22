/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import {Link} from 'react-router-dom';
import Table from 'modules/components/Table';

const List = styled.div`
  flex-grow: 1;
  position: relative;
`;

const TR = styled(Table.TR)`
  border-top: none;
`;

const TableContainer = styled.div`
  position: absolute;
  opacity: 0.9;
  height: 100%;
  width: 100%;
  left: 0;
  top: 0;
`;

const OperationsTH = styled(Table.TH)`
  width: 90px;
`;

const SelectionStatusIndicator = styled.div`
  ${({theme, selected}) => {
    const colors = theme.colors.list.selectionStatusIndicator;

    return css`
      display: inline-block;
      height: 36px;
      width: 9px;
      vertical-align: bottom;
      margin-left: -5px;
      margin-right: 12px;
      border-right: 1px solid ${colors.borderColor};
      ${selected
        ? css`
            background-color: ${theme.colors.selections};
          `
        : ''}
    `;
  }}
`;

const CheckAll = styled.div`
  ${({shouldShowOffset}) => {
    return css`
      display: inline-block;
      margin-left: ${shouldShowOffset ? 15 : 16}px;
      margin-right: 28px;
    `;
  }}
`;

const Cell = styled.div`
  position: relative;
  display: flex;
  align-items: center;

  & * {
    top: 0px;
  }
`;

const InstanceAnchor = styled(Link)`
  ${({theme}) => {
    return css`
      text-decoration: underline;

      &:link {
        color: ${theme.colors.linkDefault};
      }

      &:hover {
        color: ${theme.colors.linkHover};
      }

      &:active {
        color: ${theme.colors.linkActive};
      }

      &:visited {
        color: ${theme.colors.linkVisited};
      }
    `;
  }}
`;

const WorkflowName = styled.span`
  margin-left: 6px;
`;

const EmptyTR = styled(Table.TR)`
  border: 0;
  padding: 0;
`;

const EmptyTD = styled(Table.TD)`
  padding: 0;
`;

export {
  List,
  TR,
  TableContainer,
  OperationsTH,
  SelectionStatusIndicator,
  CheckAll,
  Cell,
  InstanceAnchor,
  WorkflowName,
  EmptyTR,
  EmptyTD,
};
