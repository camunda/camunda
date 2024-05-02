/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {
  TableHeader as BaseTableHeader,
  TableCell as BaseTableCell,
  TableRow as BaseTableRow,
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

type TableRowProps = {
  $isClickable?: boolean;
};
const TableRow = styled(BaseTableRow)<TableRowProps>`
  ${({$isClickable}) => {
    return css`
      ${$isClickable && `cursor: pointer;`}
    `;
  }}
`;

export {Container, TableHeader, TableCell, TableRow};
