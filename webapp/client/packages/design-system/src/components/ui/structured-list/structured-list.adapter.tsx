/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Carbon's StructuredList is div-based and uses CSS display:table-* to
 * achieve table layout. The adapter mirrors this approach with divs + ARIA
 * roles + Tailwind display utilities rather than mapping to a native <table>,
 * which would make <div class="cds--structured-list-tbody"> children invalid
 * HTML and cause browsers to foster-parent them outside the table element.
 *
 * Consumers that use Carbon class names directly (e.g. cds--structured-list-tbody)
 * also need `display: table-row-group` — that rule lives in globals.css.
 */

import * as React from 'react';

import {Skeleton} from '../skeleton/skeleton.shadcn';

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
    <div
      role="table"
      aria-label={ariaLabel}
      className={cn('table w-full border-collapse overflow-x-auto', className)}
      {...(rest as React.HTMLAttributes<HTMLDivElement>)}
    >
      {children}
    </div>
  );
}

function StructuredListHead(props: StructuredListHeadProps) {
  const {children, className, ...rest} = props;
  return (
    <div
      role="rowgroup"
      className={cn('table-header-group', className)}
      {...(rest as React.HTMLAttributes<HTMLDivElement>)}
    >
      {children}
    </div>
  );
}

function StructuredListBody(props: StructuredListBodyProps) {
  const {children, className, head, onKeyDown, ...rest} = props;

  warnDroppedProps('StructuredListBody', {head});

  return (
    <div
      role="rowgroup"
      className={cn('table-row-group', className)}
      onKeyDown={onKeyDown}
      {...(rest as React.HTMLAttributes<HTMLDivElement>)}
    >
      {children}
    </div>
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

  warnDroppedProps('StructuredListRow', {selection});

  return (
    <div
      id={id}
      role="row"
      className={cn(
        'table-row transition-colors',
        head ? 'border-none' : 'border-t border-border',
        className,
      )}
      onClick={onClick}
      onKeyDown={onKeyDown}
      {...(rest as React.HTMLAttributes<HTMLDivElement>)}
    >
      {children}
    </div>
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

  return (
    <div
      role={head ? 'columnheader' : 'cell'}
      className={cn(
        'table-cell px-2 py-3 text-sm',
        head ? 'align-middle font-semibold' : 'align-top',
        className,
      )}
      {...(rest as React.HTMLAttributes<HTMLDivElement>)}
    >
      {children}
    </div>
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
