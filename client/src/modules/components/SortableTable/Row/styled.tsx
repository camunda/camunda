/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import Table from 'modules/components/Table';

type TRProps = {
  isClickable: boolean;
};

const TR = styled(Table.TR)<TRProps>`
  ${({theme, isClickable, selected}) => {
    const colors = theme.colors.sortableTable;

    return css`
      line-height: 36px;
      &:first-child {
        border-top-style: hidden;
      }
      ${selected
        ? css`
            background-color: ${colors.tr.selected.backgroundColor};
          `
        : css`
            &:nth-child(odd) {
              background-color: ${theme.colors.itemOdd};
            }

            &:nth-child(even) {
              background-color: ${theme.colors.itemEven};
            }

            ${isClickable &&
            css`
              cursor: pointer;
              &:hover {
                background-color: ${colors.hover};
              }
            `};
          `}
    `;
  }}
`;

const TD = styled(Table.TD)`
  ${({theme}) => {
    return css`
      color: ${theme.colors.text01};
      &:first-child {
        padding-left: 19px;
      }
    `;
  }}
`;

export {TR, TD};
