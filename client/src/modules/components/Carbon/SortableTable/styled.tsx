/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {
  TableContainer as BaseTableContainer,
  TableCell as BaseTableCell,
  TableHead as BaseTableHead,
  DataTableSkeleton as BaseDataTableSkeleton,
} from '@carbon/react';

type ContainerProps = {
  $isScrollable: boolean;
};

const Container = styled.div<ContainerProps>`
  ${({$isScrollable}) => {
    return css`
      height: 100%;
      background-color: var(--cds-layer);
      overflow-y: ${$isScrollable ? 'auto' : 'hidden'};
      flex: 1 0 0;

      .cds--loading-overlay {
        position: absolute;
      }
    `;
  }}
`;

const TableContainer = styled(BaseTableContainer)`
  height: 100%;
  .cds--data-table-content {
    overflow-x: inherit;
  }
`;

const TableCell = styled(BaseTableCell)`
  white-space: nowrap;
`;

const TableHead = styled(BaseTableHead)`
  white-space: nowrap;
`;

const EmptyMessageContainer = styled.div`
  display: flex;
  justify-content: center;
  height: 100%;
  align-items: center;
  background-color: var(--cds-layer);
`;

const DataTableSkeleton = styled(BaseDataTableSkeleton)`
  tr {
    height: 2rem;
  }
`;

export {
  Container,
  TableContainer,
  TableCell,
  TableHead,
  EmptyMessageContainer,
  DataTableSkeleton,
};
