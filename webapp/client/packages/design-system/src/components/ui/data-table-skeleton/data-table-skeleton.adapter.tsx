/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

import {DataTableSkeleton as ShadcnDataTableSkeleton} from './data-table-skeleton.shadcn';

import type {DataTableSkeletonProps as CarbonDataTableSkeletonProps} from '@carbon/react';

export type DataTableSkeletonProps = CarbonDataTableSkeletonProps;

function DataTableSkeleton(props: DataTableSkeletonProps) {
  const {
    className,
    columnCount,
    rowCount,
    headers,
    showHeader,
    showToolbar,
    size,
    zebra,
    ...rest
  } = props as DataTableSkeletonProps & {
    className?: string;
    columnCount?: number;
    rowCount?: number;
    headers?: {header: React.ReactNode; key?: string}[];
    showHeader?: boolean;
    showToolbar?: boolean;
    size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl';
    zebra?: boolean;
  };

  return (
    <ShadcnDataTableSkeleton
      className={className}
      columnCount={columnCount}
      rowCount={rowCount}
      headers={headers}
      showHeader={showHeader}
      showToolbar={showToolbar}
      size={size}
      zebra={zebra}
      {...(rest as React.TableHTMLAttributes<HTMLTableElement>)}
    />
  );
}

export {DataTableSkeleton};
