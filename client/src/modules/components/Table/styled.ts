/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

type TRProps = {
  selected?: boolean;
};

const TR = styled.tr<TRProps>`
  ${({theme, selected}) => {
    const colors = theme.colors.modules.table.tr;

    return css`
      height: 36px;
      line-height: 37px;

      border-width: 1px 0;
      border-style: solid;
      border-color: ${theme.colors.borderColor};

      ${selected
        ? css`
            background-color: ${colors.selected.backgroundColor};
          `
        : css`
            &:nth-child(odd) {
              background-color: ${theme.colors.itemOdd};
            }

            &:nth-child(even) {
              background-color: ${theme.colors.itemEven};
            }
          `}
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
