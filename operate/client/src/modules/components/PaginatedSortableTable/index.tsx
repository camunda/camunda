/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SortableTable} from 'modules/components/SortableTable';

type PaginationProps = {
  hasPreviousPage: boolean;
  hasNextPage: boolean;
  isFetchingPreviousPage: boolean;
  isFetchingNextPage: boolean;
  fetchPreviousPage: () => Promise<unknown>;
  fetchNextPage: () => Promise<unknown>;
};

const ROW_HEIGHTS: Record<
  NonNullable<React.ComponentProps<typeof SortableTable>['size']>,
  number
> = {
  xs: 24,
  sm: 32,
  md: 40,
  lg: 48,
  xl: 64,
};

type SortableTableProps = React.ComponentProps<typeof SortableTable>;

type PaginatedSortableTableProps<
  RowType extends Record<string, unknown>,
  ColTypes extends React.ReactNode[],
> = Omit<
  React.ComponentProps<typeof SortableTable<RowType, ColTypes>>,
  'onVerticalScrollStartReach' | 'onVerticalScrollEndReach'
> & {
  pagination: PaginationProps;
};

function getScrollStepSize(size: SortableTableProps['size']) {
  return 5 * ROW_HEIGHTS[size ?? 'sm'];
}

export function PaginatedSortableTable<
  RowType extends Record<string, unknown> = Record<string, unknown>,
  ColTypes extends React.ReactNode[] = React.ReactNode[],
>({pagination, ...tableProps}: PaginatedSortableTableProps<RowType, ColTypes>) {
  const {
    hasPreviousPage,
    hasNextPage,
    isFetchingPreviousPage,
    isFetchingNextPage,
    fetchPreviousPage,
    fetchNextPage,
  } = pagination;

  const handleScrollStartReach: SortableTableProps['onVerticalScrollStartReach'] =
    async (scrollDown) => {
      if (!hasPreviousPage || isFetchingPreviousPage) {
        return;
      }

      await fetchPreviousPage();
      scrollDown(getScrollStepSize(tableProps.size));
    };

  const handleScrollEndReach: SortableTableProps['onVerticalScrollEndReach'] =
    () => {
      if (!hasNextPage || isFetchingNextPage) {
        return;
      }

      fetchNextPage();
    };

  return (
    <SortableTable<RowType, ColTypes>
      {...tableProps}
      onVerticalScrollStartReach={handleScrollStartReach}
      onVerticalScrollEndReach={handleScrollEndReach}
    />
  );
}
