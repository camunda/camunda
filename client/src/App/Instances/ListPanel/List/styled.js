/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled from 'styled-components';
import {Link} from 'react-router-dom';
import Table from 'modules/components/Table';

import {Colors, themed, themeStyle} from 'modules/theme';

export const List = styled.div`
  flex-grow: 1;
  position: relative;
`;

export const TR = styled(Table.TR)`
  border-top: none;
`;

export const TableContainer = styled.div`
  position: absolute;
  opacity: 0.9;
  height: 100%;
  width: 100%;
  left: 0;
  top: 0;
`;

export const ActionsTH = styled(Table.TH)`
  width: 90px;
`;

export const SelectionStatusIndicator = themed(styled.div`
  display: inline-block;
  height: 36px;
  width: 9px;
  ${({selected}) => selected && `background-color: ${Colors.selections};`};
  vertical-align: bottom;
  margin-left: -5px;
  margin-right: 11px;

  border-right: 1px solid
    ${themeStyle({dark: Colors.uiDark04, light: Colors.uiLight05})};
`);

export const CheckAll = styled.div`
  display: inline-block;
  margin-left: 16px;
  margin-right: 28px;
`;

export const Cell = styled.div`
  position: relative;
  display: flex;
  align-items: center;

  & * {
    top: 0px;
  }
`;

export const InstanceAnchor = themed(styled(Link)`
  text-decoration: underline;

  &:link {
    color: ${themeStyle({
      dark: Colors.darkLinkDefault,
      light: Colors.lightLinkDefault
    })};
  }

  &:hover {
    color: ${themeStyle({
      dark: Colors.darkLinkHover,
      light: Colors.lightLinkHover
    })};
  }

  &:active {
    color: ${themeStyle({
      dark: Colors.darkLinkActive,
      light: Colors.lightLinkActive
    })};
  }

  &:visited {
    color: ${themeStyle({
      dark: Colors.darkLinkVisited,
      light: Colors.lightLinkVisited
    })};
  }
`);

export const WorkflowName = styled.span`
  margin-left: 6px;
`;

export const EmptyTR = styled(Table.TR)`
  border: 0;
  padding: 0;
`;

export const EmptyTD = styled(Table.TD)`
  padding: 0;
`;
