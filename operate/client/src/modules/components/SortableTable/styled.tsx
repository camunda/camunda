/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import styled, {css} from 'styled-components';
import {
  TableContainer as BaseTableContainer,
  TableCell as BaseTableCell,
  TableHead as BaseTableHead,
  DataTableSkeleton as BaseDataTableSkeleton,
  TableExpandRow as BaseTableExpandRow,
  TableRow as BaseTableHeadRow,
  TableExpandedRow as BaseTableExpandedRow,
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

type TableExpandRowProps = {
  $isClickable?: boolean;
};

const TableExpandRow = styled(BaseTableExpandRow)<TableExpandRowProps>`
  // remove extra top border except for first row
  &:not(:first-child) td {
    border-block-start: none !important;
  }

  // hide expand button on non-error rows
  &:not(.errorRow) {
    .cds--table-expand__button {
      visibility: hidden;
    }
  }

  // add success border
  &.successRow {
    box-shadow: inset 3px 0 0 var(--cds-support-success);
  }

  ${({$isClickable}) => {
    return css`
      ${$isClickable && `cursor: pointer;`}
    `;
  }}
`;

const TableExpandedRow = styled(BaseTableExpandedRow)`
  box-shadow: inset 3px 0 0 var(--cds-support-error) !important;

  td {
    border-block-start: none !important;
  }

  &:hover td {
    box-shadow: inset 3px 0 0 var(--cds-support-error) !important;
  }
`;

const TableContainer = styled(BaseTableContainer)`
  height: 100%;
  .cds--data-table-content {
    overflow-x: inherit;
  }

  tr.errorRow {
    // show button to expand on error row
    .cds--table-expand__button {
      visibility: visible;
    }

    // add parent error border (opened and closed)
    box-shadow: inset 3px 0 0 var(--cds-support-error) !important;

    // remove unwanted lines and borders on parent (opened)
    &.cds--expandable-row td {
      box-shadow: none;
      border-block-end: none !important;
    }

    // add parent error border (opened) on child hover
    &.cds--expandable-row--hover td {
      &:first-child {
        box-shadow: inset 3px 0 0 var(--cds-support-error) !important;
      }
    }

    &:hover {
      // HOVER - add parent error border (opened and closed)
      td.cds--table-expand {
        box-shadow: inset 3px 0 0 var(--cds-support-error) !important;
      }

      // HOVER - remove unwanted borders
      &.cds--expandable-row td {
        border-block-end: none !important;
      }

      // HOVER - add child error border
      + ${TableExpandedRow} td {
        box-shadow: inset 3px 0 0 var(--cds-support-error) !important;
      }
    }
  }
`;

type TableCellProps = {
  $hideCellPadding?: boolean;
};

const TableCell = styled(BaseTableCell)<TableCellProps>`
  ${({$hideCellPadding}) => {
    return css`
      white-space: nowrap;
      ${$hideCellPadding &&
      css`
        white-space: normal;
        padding-top: 0 !important;
        padding-bottom: 0 !important;
      `}
    `;
  }}
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

const TableHeadRow = styled(BaseTableHeadRow)<{$isClickable?: boolean}>`
  ${({$isClickable}) => {
    return css`
      ${$isClickable && `cursor: pointer;`}
    `;
  }}
`;

export {
  Container,
  TableContainer,
  TableCell,
  TableHead,
  EmptyMessageContainer,
  DataTableSkeleton,
  TableHeadRow,
  TableExpandRow,
  TableExpandedRow,
};
