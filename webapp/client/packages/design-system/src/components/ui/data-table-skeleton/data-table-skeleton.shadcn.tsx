/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {cn} from '../../../lib/utils';
import {Skeleton} from '../skeleton/skeleton.shadcn';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../table/table.shadcn';

type DataTableSkeletonHeader = {
  header: React.ReactNode;
  key?: string;
};

type DataTableSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';

const ROW_HEIGHT_BY_SIZE: Record<DataTableSize, string> = {
  xs: 'h-6',
  sm: 'h-8',
  md: 'h-10',
  lg: 'h-12',
  xl: 'h-16',
};

type DataTableSkeletonProps = Omit<
  React.TableHTMLAttributes<HTMLTableElement>,
  'children'
> & {
  columnCount?: number;
  rowCount?: number;
  headers?: DataTableSkeletonHeader[];
  showHeader?: boolean;
  showToolbar?: boolean;
  size?: DataTableSize;
  zebra?: boolean;
};

function DataTableSkeleton({
  className,
  columnCount = 5,
  rowCount = 5,
  headers,
  showHeader = true,
  showToolbar = true,
  size = 'md',
  zebra,
  ...rest
}: DataTableSkeletonProps) {
  const cols =
    headers && headers.length > 0
      ? headers
      : Array.from({length: columnCount}, (_, i) => ({key: String(i), header: null}));

  const rowHeight = ROW_HEIGHT_BY_SIZE[size];

  return (
    <div data-slot="data-table-skeleton" className={cn('w-full', className)}>
      {showToolbar && (
        <div
          data-slot="data-table-skeleton-toolbar"
          className="flex h-12 items-center justify-end gap-2 border-b border-border px-2"
        >
          <Skeleton className="h-8 w-32" />
          <Skeleton className="h-8 w-24" />
        </div>
      )}
      {showHeader && (
        <div
          data-slot="data-table-skeleton-heading"
          className="flex flex-col gap-1.5 px-4 pt-3 pb-2"
        >
          <Skeleton className="h-5 w-48" />
          <Skeleton className="h-4 w-72" />
        </div>
      )}
      <Table {...rest}>
        <TableHeader>
          <TableRow>
            {cols.map((col, i) => (
              <TableHead
                key={col.key ?? String(i)}
                className={cn(rowHeight, 'align-middle')}
              >
                {col.header ?? <Skeleton className="h-3.5 w-24" />}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {Array.from({length: rowCount}, (_, r) => (
            <TableRow
              key={r}
              className={cn(zebra && r % 2 === 1 && 'bg-muted/30')}
            >
              {cols.map((col, c) => (
                <TableCell
                  key={col.key ?? String(c)}
                  className={cn(rowHeight, 'align-middle')}
                >
                  <Skeleton className="h-3.5 w-full max-w-[12rem]" />
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

export {DataTableSkeleton};
export type {DataTableSkeletonProps, DataTableSkeletonHeader, DataTableSize};
