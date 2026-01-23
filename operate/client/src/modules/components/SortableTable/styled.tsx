/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
  onClick?: React.MouseEventHandler<HTMLTableRowElement>;
};

const TableExpandRow = styled(BaseTableExpandRow)<TableExpandRowProps>`
  // remove extra top border except for first row
  &:not(:first-child) td {
    border-block-start: none !important;
  }

  &:not(.errorRow):not(.successRow) {
    // hide expand button when batchOperationId filter is NOT set
    .cds--table-expand {
      display: none;
    }

    // fix spacing after checkbox when batchOperationId filter is NOT set
    .cds--table-column-checkbox {
      padding-inline-start: 1rem;
      + td {
        padding-inline-start: 1rem;
      }
    }
  }

  &.successRow {
    // hide button to expand row on success rows
    .cds--table-expand button {
      visibility: hidden;
    }

    // add success border
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

type TableContainerProps = {
  $hasError: boolean;
};

const TableContainer = styled(BaseTableContainer)<TableContainerProps>`
  height: 100%;
  .cds--data-table-content {
    overflow-x: inherit;
  }

  // set bottom border for header row as transparent
  * {
    border-block-start-color: transparent !important;
  }

  // fix spacing between checkbox and next cell in header row
  th.cds--table-column-checkbox + th {
    padding-inline-start: 0 !important;

    button {
      padding-left: ${({$hasError}) => ($hasError ? '0.5rem' : '1rem')};
    }
  }

  // fix spacing before checkbox when batchOperationId filter is set
  tr.errorRow,
  tr.successRow {
    .cds--table-column-checkbox {
      + td {
        padding-inline-start: 0.375rem;
      }
    }
  }

  tr.errorRow {
    // show button to expand on error row
    .cds--table-expand__button {
      visibility: visible;
    }

    &:not(.cds--expandable-row) + ${TableExpandedRow} {
      visibility: hidden;
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

type TableHeadProps = {
  $stickyHeader?: boolean;
};

const TableHead = styled(BaseTableHead)<TableHeadProps>`
  ${({$stickyHeader}) => {
    return css`
      white-space: nowrap;
      ${$stickyHeader &&
      css`
        position: sticky;
        top: 0;
        z-index: 1;
      `}
    `;
  }}
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
