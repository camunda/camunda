/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const Table = styled.table`
  width: 100%;
  font-size: 14px;
  border-spacing: 0;
  border-collapse: collapse;
`;

const TH = styled.th`
  ${({theme}) => {
    const colors = theme.colors.modules.table.th;

    return css`
      font-weight: 600;
      padding: 0 0 0 5px;
      color: ${colors.color};

      &:not(:last-child):after {
        content: ' ';
        float: right;
        height: 31px;
        margin-top: 3px;
        width: 1px;
        background: ${colors.after.backgroundColor};
      }
    `;
  }}
`;

const TD = styled.td`
  ${({theme}) => {
    const colors = theme.colors.modules.table.td;

    return css`
      padding: 0 0 0 5px;
      white-space: nowrap;
      color: ${colors.color};
    `;
  }}
`;

const TR = styled.tr`
  ${({theme, selected}) => {
    const colors = theme.colors.modules.table.tr;

    return css`
      height: 36px;
      line-height: 37px;

      border-width: 1px 0;
      border-style: solid;
      border-color: ${colors.borderColor};

      &:nth-child(odd) {
        background-color: ${selected
          ? theme.colors.selectedOdd
          : colors.odd.backgroundColor};
      }

      &:nth-child(even) {
        background-color: ${selected
          ? theme.colors.selectedEven
          : theme.colors.itemEven};
      }
    `;
  }}
`;

const THead = styled.thead`
  ${({theme}) => {
    const colors = theme.colors.modules.table.thead;

    return css`
      text-align: left;
      background-color: ${colors.backgroundColor};

      ${TR} {
        background-color: ${colors.tr.backgroundColor};
      }
    `;
  }}
`;

export {Table, TH, TD, TR, THead};
