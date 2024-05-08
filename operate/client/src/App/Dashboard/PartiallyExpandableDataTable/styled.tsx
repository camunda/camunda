/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {
  TableCell as BaseTableCell,
  Table as BaseTable,
  TableExpandRow as BaseTableExpandRow,
  DataTableSkeleton as BaseDataTableSkeleton,
  TableExpandedRow as BaseTableExpandedRow,
  TableHead as BaseTableHead,
} from '@carbon/react';

import {ARROW_ICON_WIDTH} from 'modules/constants';

const Table = styled(BaseTable)`
  table-layout: fixed;
`;

const TableHead = styled(BaseTableHead)`
  display: none;
`;

type Props = {
  $isExpandable: boolean;
};

const TableExpandRow = styled(BaseTableExpandRow)<Props>`
  ${({$isExpandable}) => {
    return css`
      .cds--table-expand {
        width: ${ARROW_ICON_WIDTH} !important;
        button {
          height: var(--cds-spacing-07) !important;
        }
      }

      // override the background color of the expandable row when child rows are hovered.
      &.cds--expandable-row--hover td {
        background-color: var(--cds-layer-01) !important;
      }

      ${!$isExpandable &&
      css`
        button {
          display: none;
        }
      `}
    `;
  }}
`;

const tableCellStyles = css`
  padding-top: 0 !important;
  padding-bottom: 0 !important;
`;

const ExpandableTableCell = styled(BaseTableCell)`
  ${tableCellStyles}
`;

const DataTableSkeleton = styled(BaseDataTableSkeleton)`
  thead {
    display: none;
  }

  span {
    width: auto !important;
  }
`;

const TableExpandedRow = styled(BaseTableExpandedRow)`
  td {
    ${tableCellStyles};

    // override the default background color of expandable row's children
    background-color: var(--cds-layer) !important;
  }
`;

export {
  ExpandableTableCell,
  Table,
  TableExpandRow,
  DataTableSkeleton,
  TableExpandedRow,
  TableHead,
};
