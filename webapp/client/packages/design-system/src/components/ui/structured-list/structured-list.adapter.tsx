/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's StructuredList family maps to shadcn's Table primitive — both
 * are tabular layouts. The selection variant (radio-row) is not modelled
 * here; consumers must compose with `radio-group` when migrating that case.
 */

import * as React from 'react';

import {Skeleton} from '../skeleton/skeleton.shadcn';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../table/table.shadcn';

import {cn, warnDroppedProps} from '../../../lib/utils';

import type {
  StructuredListBodyProps as CarbonStructuredListBodyProps,
  StructuredListCellProps as CarbonStructuredListCellProps,
  StructuredListHeadProps as CarbonStructuredListHeadProps,
  StructuredListInputProps as CarbonStructuredListInputProps,
  StructuredListRowProps as CarbonStructuredListRowProps,
  StructuredListSkeletonProps as CarbonStructuredListSkeletonProps,
  StructuredListWrapperProps as CarbonStructuredListWrapperProps,
} from '@carbon/react';

export type StructuredListWrapperProps = CarbonStructuredListWrapperProps;
export type StructuredListHeadProps = CarbonStructuredListHeadProps;
export type StructuredListBodyProps = CarbonStructuredListBodyProps;
export type StructuredListRowProps = CarbonStructuredListRowProps;
export type StructuredListCellProps = CarbonStructuredListCellProps;
export type StructuredListInputProps = CarbonStructuredListInputProps;
export type StructuredListSkeletonProps = CarbonStructuredListSkeletonProps;

function StructuredListWrapper(props: StructuredListWrapperProps) {
  const {
    children,
    className,
    isCondensed,
    isFlush,
    selection,
    selectedInitialRow,
    'aria-label': ariaLabel,
    ...rest
  } = props;

  warnDroppedProps('StructuredListWrapper', {
    isCondensed,
    isFlush,
    selection,
    selectedInitialRow,
  });

  return (
    <Table
      aria-label={ariaLabel}
      className={cn(className)}
      {...(rest as React.ComponentProps<typeof Table>)}
    >
      {children}
    </Table>
  );
}

function StructuredListHead(props: StructuredListHeadProps) {
  const {children, className, ...rest} = props;
  return (
    <TableHeader
      className={cn(className)}
      {...(rest as React.ComponentProps<typeof TableHeader>)}
    >
      {children}
    </TableHeader>
  );
}

function StructuredListBody(props: StructuredListBodyProps) {
  const {children, className, head, onKeyDown, ...rest} = props;

  warnDroppedProps('StructuredListBody', {head});

  return (
    <TableBody
      className={cn(className)}
      onKeyDown={onKeyDown}
      {...(rest as React.ComponentProps<typeof TableBody>)}
    >
      {children}
    </TableBody>
  );
}

function StructuredListRow(props: StructuredListRowProps) {
  const {
    children,
    className,
    head,
    onClick,
    onKeyDown,
    selection,
    id,
    ...rest
  } = props;

  warnDroppedProps('StructuredListRow', {head, selection});

  return (
    <TableRow
      id={id}
      className={cn(className)}
      onClick={onClick}
      onKeyDown={onKeyDown}
      {...(rest as React.ComponentProps<typeof TableRow>)}
    >
      {children}
    </TableRow>
  );
}

type StructuredListCellExtras = {
  head?: boolean;
  noWrap?: boolean;
};

function StructuredListCell(
  props: StructuredListCellProps & StructuredListCellExtras,
) {
  const {children, className, head, noWrap, ...rest} = props;

  warnDroppedProps('StructuredListCell', {noWrap});

  if (head) {
    return (
      <TableHead
        className={cn(className)}
        {...(rest as React.ComponentProps<typeof TableHead>)}
      >
        {children}
      </TableHead>
    );
  }

  return (
    <TableCell
      className={cn(className)}
      {...(rest as React.ComponentProps<typeof TableCell>)}
    >
      {children}
    </TableCell>
  );
}

function StructuredListInput(props: StructuredListInputProps) {
  const {className, ...rest} = props as StructuredListInputProps & {
    className?: string;
  };
  return (
    <input
      type="radio"
      className={cn('sr-only', className)}
      {...(rest as React.InputHTMLAttributes<HTMLInputElement>)}
    />
  );
}

function StructuredListSkeleton(props: StructuredListSkeletonProps) {
  const {className, ...rest} = props as StructuredListSkeletonProps & {
    className?: string;
    rowCount?: number;
  };
  const rowCount = (rest as {rowCount?: number}).rowCount ?? 5;

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      {Array.from({length: rowCount}).map((_, index) => (
        <Skeleton key={index} className="h-8 w-full" />
      ))}
    </div>
  );
}

export {
  StructuredListBody,
  StructuredListCell,
  StructuredListHead,
  StructuredListInput,
  StructuredListRow,
  StructuredListSkeleton,
  StructuredListWrapper,
};
