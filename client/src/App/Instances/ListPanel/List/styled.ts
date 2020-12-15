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
  overflow: auto;
`;

const TRHeader = styled(Table.TR)`
  border-top: none;
  height: 38px;
`;

const TR = styled(Table.TR)`
  &:first-child {
    border-top-style: hidden;
  }
`;

const TableContainer = styled.div`
  position: absolute;
  opacity: 0.9;
  height: 100%;
  width: 100%;
  left: 0;
  top: 0;
`;

const THPositionStyles = css`
  position: sticky;
  top: 0;
  z-index: 1000;
`;

const OperationsTH = styled(Table.TH)`
  ${({theme}) => {
    return css`
      ${THPositionStyles}
      width: 90px;
      background-color: ${theme.colors.ui02};
      box-shadow: inset 0 -1px 0 ${theme.colors.ui05};
    `;
  }}
`;

const THead = styled(Table.THead)`
  height: 37px;
  position: sticky;
  top: 0;
  z-index: 1000;
`;

const TH = styled(Table.TH)`
  ${({theme}) => {
    return css`
      ${THPositionStyles}
      background-color: ${theme.colors.ui02};
      box-shadow: inset 0 -1px 0 ${theme.colors.ui05};
    `;
  }}
`;

type SelectionStatusIndicatorProps = {
  selected?: boolean;
};

const SelectionStatusIndicator = styled.div<SelectionStatusIndicatorProps>`
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

type CheckAllProps = {
  shouldShowOffset?: boolean;
};

const CheckAll = styled.div<CheckAllProps>`
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
  TRHeader,
  TableContainer,
  OperationsTH,
  SelectionStatusIndicator,
  CheckAll,
  Cell,
  InstanceAnchor,
  WorkflowName,
  EmptyTR,
  EmptyTD,
  THead,
  TH,
  TR,
};
