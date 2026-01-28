/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useCallback} from 'react';
import {SortableTable} from 'modules/components/SortableTable';
import {useBatchOperationItems} from 'modules/queries/batch-operations/useBatchOperationItems';
import {BatchStateIndicator} from 'App/BatchOperations/BatchStateIndicator';
import {formatDate} from 'modules/utils/date';

const TABLE_HEADERS = [
  {key: 'processInstanceKey', header: 'Process Instance Key', isDisabled: true},
  {key: 'state', header: 'Batch state', isDisabled: true},
  {key: 'processedDate', header: 'Time', sortKey: 'processedDate'},
];

type Props = {
  batchOperationKey: string;
  isLoading: boolean;
};

export const BatchItemsTable: React.FC<Props> = ({
  batchOperationKey,
  isLoading,
}) => {
  const {
    data,
    status,
    isFetching,
    isFetched,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isFetchingPreviousPage,
  } = useBatchOperationItems({
    filter: {batchOperationKey},
    sort: [{field: 'processedDate', order: 'desc'}],
  });

  const items = useMemo(
    () => data?.pages.flatMap((page) => page.items) ?? [],
    [data],
  );

  const totalItems = data?.pages?.[0]?.page?.totalItems ?? 0;

  const rows = useMemo(
    () =>
      items.map((item) => ({
        id: item.itemKey.toString(),
        processInstanceKey: item.processInstanceKey,
        state: <BatchStateIndicator status={item.state} />,
        processedDate: formatDate(item.processedDate ?? ''),
      })),
    [items],
  );

  const rowOperationError = useCallback(
    (rowId: string) => {
      const item = items.find((item) => item.itemKey.toString() === rowId);
      return item?.errorMessage ? (
        <>
          <strong>Failure reason:</strong> {item.errorMessage}
        </>
      ) : null;
    },
    [items],
  );

  const getTableState = () => {
    switch (true) {
      case isLoading:
      case status === 'pending' && !data:
        return 'skeleton';
      case isFetched && items.length === 0:
        return 'empty';
      case isFetching && !isFetchingPreviousPage && !isFetchingNextPage:
        return 'loading';
      case status === 'error':
        return 'error';

      default:
        return 'content';
    }
  };

  return (
    <>
      {totalItems > 0 && <strong>{totalItems} Items</strong>}
      <SortableTable
        batchOperationId={batchOperationKey}
        state={getTableState()}
        rows={rows}
        headerColumns={TABLE_HEADERS}
        rowOperationError={rowOperationError}
        onVerticalScrollEndReach={() => {
          if (hasNextPage && !isFetchingNextPage) {
            fetchNextPage();
          }
        }}
        emptyMessage={{message: 'No items found'}}
        stickyHeader
      />
    </>
  );
};
