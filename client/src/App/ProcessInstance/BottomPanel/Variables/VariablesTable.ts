/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';

const TH = styled.th`
  ${styles.bodyShort01};
  text-align: left;
  padding-left: 20px;
  height: 31px;
`;

type TRProps = {
  hasActiveOperation?: boolean;
};
const TR = styled.tr<TRProps>`
  ${({theme, hasActiveOperation}) => {
    const colors = theme.colors.variables.variablesTable.tr;

    return css`
      border-width: 1px 0;
      border-style: solid;
      border-color: ${theme.colors.borderColor};

      &:first-child {
        border-top: none;
      }

      &:last-child {
        border-bottom: none;
      }

      > td:first-child {
        width: 30%;
        max-width: 0;
        padding-right: 23px;
      }
      > td:nth-child(2) {
        width: 70%;
        padding-left: 0;
      }

      ${hasActiveOperation
        ? css`
            background-color: ${colors.backgroundColor};
          `
        : ''};
    `;
  }}
`;

const Table = styled.table`
  width: 100%;
  min-width: 400px;
  margin-bottom: 3px;
  border-spacing: 0;
  border-collapse: collapse;
`;

export {TH, TR, Table};
