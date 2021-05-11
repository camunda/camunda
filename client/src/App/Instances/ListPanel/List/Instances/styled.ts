/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import Table from 'modules/components/Table';
import {Link} from 'react-router-dom';

const TR = styled(Table.TR)`
  &:first-child {
    border-top-style: hidden;
  }
`;

const Cell = styled.div`
  position: relative;
  display: flex;
  align-items: center;

  & * {
    top: 0px;
  }
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

const ProcessName = styled.span`
  margin-left: 6px;
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

export {TR, Cell, SelectionStatusIndicator, ProcessName, InstanceAnchor};
