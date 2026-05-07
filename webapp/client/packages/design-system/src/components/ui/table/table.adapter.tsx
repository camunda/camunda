/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Naming flip: Carbon `TableHead` = `<thead>` (== shadcn `TableHeader`),
 * Carbon `TableHeader` = `<th>` (== shadcn `TableHead`). Adapter re-aliases.
 */

import * as React from 'react';

import {
  Table as ShadcnTable,
  TableBody as ShadcnTableBody,
  TableCell as ShadcnTableCell,
  TableHead as ShadcnTableHead,
  TableHeader as ShadcnTableHeader,
  TableRow as ShadcnTableRow,
} from './table.shadcn';

import {warnDroppedProps} from '../../../lib/utils';

import type {
  TableBodyProps as CarbonTableBodyProps,
  TableCellProps as CarbonTableCellProps,
  TableHeadProps as CarbonTableHeadProps,
  TableHeaderProps as CarbonTableHeaderProps,
  TableRowProps as CarbonTableRowProps,
} from '@carbon/react';

export type TableProps = React.ComponentProps<'table'> & {
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl';
  isSortable?: boolean;
  useStaticWidth?: boolean;
  useZebraStyles?: boolean;
  stickyHeader?: boolean;
  overflowMenuOnHover?: boolean;
  experimentalAutoAlign?: boolean;
};
export type TableBodyProps = CarbonTableBodyProps;
export type TableCellProps = CarbonTableCellProps;
export type TableHeadProps = CarbonTableHeadProps;
export type TableHeaderProps = CarbonTableHeaderProps;
export type TableRowProps = CarbonTableRowProps;

function Table(props: TableProps) {
  const {
    children,
    className,
    size,
    isSortable,
    useStaticWidth,
    useZebraStyles,
    stickyHeader,
    overflowMenuOnHover,
    experimentalAutoAlign,
    ...rest
  } = props as TableProps & {
    children?: React.ReactNode;
    className?: string;
    size?: string;
    isSortable?: boolean;
    useStaticWidth?: boolean;
    useZebraStyles?: boolean;
    stickyHeader?: boolean;
    overflowMenuOnHover?: boolean;
    experimentalAutoAlign?: boolean;
  };

  warnDroppedProps('Table', {
    size,
    isSortable,
    useStaticWidth,
    useZebraStyles,
    stickyHeader,
    overflowMenuOnHover,
    experimentalAutoAlign,
  });

  return (
    <ShadcnTable
      className={className}
      {...(rest as React.ComponentProps<typeof ShadcnTable>)}
    >
      {children}
    </ShadcnTable>
  );
}

function TableBody(props: TableBodyProps) {
  const {children, className, ...rest} = props as TableBodyProps & {
    children?: React.ReactNode;
    className?: string;
  };
  return (
    <ShadcnTableBody
      className={className}
      {...(rest as React.ComponentProps<typeof ShadcnTableBody>)}
    >
      {children}
    </ShadcnTableBody>
  );
}

function TableCell(props: TableCellProps) {
  const {children, className, ...rest} = props as TableCellProps & {
    children?: React.ReactNode;
    className?: string;
  };
  return (
    <ShadcnTableCell
      className={className}
      {...(rest as React.ComponentProps<typeof ShadcnTableCell>)}
    >
      {children}
    </ShadcnTableCell>
  );
}

// Carbon `TableHead` = `<thead>` → shadcn `TableHeader`
function TableHead(props: TableHeadProps) {
  const {children, className, ...rest} = props as TableHeadProps & {
    children?: React.ReactNode;
    className?: string;
  };
  return (
    <ShadcnTableHeader
      className={className}
      {...(rest as React.ComponentProps<typeof ShadcnTableHeader>)}
    >
      {children}
    </ShadcnTableHeader>
  );
}

// Carbon `TableHeader` = `<th>` → shadcn `TableHead`
function TableHeader(props: TableHeaderProps) {
  const {
    children,
    className,
    isSortable,
    isSortHeader,
    sortDirection,
    onClick,
    scope,
    colSpan,
    translateWithId,
    ...rest
  } = props as TableHeaderProps & {
    children?: React.ReactNode;
    className?: string;
    isSortable?: boolean;
    isSortHeader?: boolean;
    sortDirection?: string;
    onClick?: React.MouseEventHandler<HTMLElement>;
    scope?: React.ThHTMLAttributes<HTMLTableCellElement>['scope'];
    colSpan?: number;
    translateWithId?: unknown;
  };

  warnDroppedProps('TableHeader', {
    isSortable,
    isSortHeader,
    sortDirection,
    translateWithId,
  });

  return (
    <ShadcnTableHead
      className={className}
      onClick={onClick}
      scope={scope}
      colSpan={colSpan}
      {...(rest as React.ComponentProps<typeof ShadcnTableHead>)}
    >
      {children}
    </ShadcnTableHead>
  );
}

function TableRow(props: TableRowProps) {
  const {children, className, ...rest} = props as TableRowProps & {
    children?: React.ReactNode;
    className?: string;
  };
  return (
    <ShadcnTableRow
      className={className}
      {...(rest as React.ComponentProps<typeof ShadcnTableRow>)}
    >
      {children}
    </ShadcnTableRow>
  );
}

export {Table, TableBody, TableCell, TableHead, TableHeader, TableRow};
