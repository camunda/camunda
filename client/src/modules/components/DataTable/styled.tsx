/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {
  TableHeader as BaseTableHeader,
  TableCell as BaseTableCell,
} from '@carbon/react';

const Container = styled.div`
  overflow-y: auto;
`;

type TableHeaderProps = {
  $width?: string;
};

const TableHeader = styled(BaseTableHeader)<TableHeaderProps>`
  ${({$width}) => {
    return css`
      ${$width !== undefined &&
      css`
        width: ${$width};
      `}
    `;
  }}
`;

type TableCellProps = {
  $hideCellPadding?: boolean;
};

const TableCell = styled(BaseTableCell)<TableCellProps>`
  ${({$hideCellPadding}) => {
    return css`
      ${$hideCellPadding &&
      css`
        padding-top: 0 !important;
        padding-bottom: 0 !important;
      `}
    `;
  }}
`;

export {Container, TableHeader, TableCell};
