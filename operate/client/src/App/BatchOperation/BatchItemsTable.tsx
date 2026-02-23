/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useCallback} from 'react';
import {PaginatedSortableTable} from 'modules/components/PaginatedSortableTable';
import {Link} from 'react-router-dom';
import {useBatchOperationItems} from 'modules/queries/batch-operations/useBatchOperationItems';
import {BatchStateIndicator} from 'App/BatchOperations/BatchStateIndicator';
import {formatDate} from 'modules/utils/date';
import {Paths} from 'modules/Routes';

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
    hasPreviousPage,
    fetchPreviousPage,
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
      items.map(({itemKey, processInstanceKey, state, processedDate}) => ({
        id: itemKey.toString(),
        processInstanceKey: (
          <Link
            to={Paths.processInstance(processInstanceKey)}
            title={`View process instance ${processInstanceKey}`}
            aria-label={`View process instance ${processInstanceKey}`}
          >
            {processInstanceKey}
          </Link>
        ),
        state: <BatchStateIndicator status={state} />,
        processedDate: formatDate(processedDate ?? ''),
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
      <PaginatedSortableTable
        batchOperationId={batchOperationKey}
        state={getTableState()}
        rows={rows}
        headerColumns={TABLE_HEADERS}
        rowOperationError={rowOperationError}
        emptyMessage={{message: 'No items found'}}
        stickyHeader
        pagination={{
          hasPreviousPage,
          hasNextPage,
          isFetchingPreviousPage,
          isFetchingNextPage,
          fetchPreviousPage,
          fetchNextPage,
        }}
      />
    </>
  );
};
