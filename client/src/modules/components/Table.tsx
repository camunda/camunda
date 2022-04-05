/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';
import {rgba} from 'polished';

interface TRProps {
  hasNoBorder?: boolean;
}

const Table = styled.table`
  width: 100%;
  font-size: 14px;
  border-collapse: collapse;
`;

const RowTH = styled.th`
  width: 198px;
  padding: 12px 20px;
  text-align: left;
  color: ${({theme}) => rgba(theme.colors.ui07, 0.9)};
`;

const ColumnTH = styled.th`
  font-weight: normal;
  color: ${({theme}) => theme.colors.label01};
  text-align: left;
  padding: 12px 12px 7px 0;

  &:first-child {
    padding-left: 20px;
  }
  &:nth-child(2) {
    padding-left: 4px;
  }
`;

const TD = styled.td`
  padding: 12px;
  color: ${({theme}) => theme.colors.ui06};
  word-break: break-all;
`;

const TR = styled.tr<TRProps>`
  ${({hasNoBorder, theme}) =>
    !hasNoBorder && `border-bottom: 1px solid ${theme.colors.ui05};`}
`;

export {Table, RowTH, ColumnTH, TR, TD};
