/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {
  TableCell as BaseTableCell,
  Table as BaseTable,
  TableExpandRow as BaseTableExpandRow,
  DataTableSkeleton as BaseDataTableSkeleton,
  TableExpandedRow as BaseTableExpandedRow,
} from '@carbon/react';
import {ARROW_ICON_WIDTH} from 'modules/constants';

const DEFAULT_TABLE_CELL_PADDING = 'var(--cds-spacing-05)';

const Table = styled(BaseTable)`
  table-layout: fixed;
`;

const TableExpandRow = styled(BaseTableExpandRow)`
  .cds--table-expand {
    width: ${ARROW_ICON_WIDTH} !important;
    button {
      height: var(--cds-spacing-07) !important;
    }
  }
`;

const tableCellStyles = css`
  padding-top: 0 !important;
  padding-bottom: 0 !important;
`;

const TableCell = styled(BaseTableCell)`
  ${tableCellStyles}
  padding-left: calc(
    ${ARROW_ICON_WIDTH} + ${DEFAULT_TABLE_CELL_PADDING}
  ) !important;
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
    ${tableCellStyles}
  }
`;

export {
  TableCell,
  ExpandableTableCell,
  Table,
  TableExpandRow,
  DataTableSkeleton,
  TableExpandedRow,
};
