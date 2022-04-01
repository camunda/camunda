/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';
import Table from 'modules/components/Table';

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
    return css`
      display: inline-block;
      height: 36px;
      width: 9px;
      vertical-align: bottom;
      margin-left: -5px;
      margin-right: 12px;
      border-right: 1px solid ${theme.colors.borderColor};
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

export {TR, Cell, SelectionStatusIndicator, ProcessName};
