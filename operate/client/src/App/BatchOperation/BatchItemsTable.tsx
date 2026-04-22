/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useCallback} from 'react';
import {PaginatedSortableTable} from 'modules/components/PaginatedSortableTable';
import {useBatchOperationItems} from 'modules/queries/batch-operations/useBatchOperationItems';
import {BatchStateIndicator} from 'App/BatchOperations/BatchStateIndicator';
import {formatDate} from 'modules/utils/date';
import {Paths} from 'modules/Routes';
import {PanelHeader as BasePanelHeader} from 'modules/components/PanelHeader';
import type {BatchOperationType} from '@camunda/camunda-api-zod-schemas/8.9';
import {ItemKeyCell} from './ItemKeyCell';
import {TableContainer} from './BatchItemsTable.styled';

const COMMON_HEADER_COLUMNS = [
  {key: 'state', header: 'Batch state', isDisabled: true},
  {key: 'processedDate', header: 'Date', sortKey: 'processedDate'},
];

const DEFAULT_HEADER_COLUMNS = [
  {key: 'processInstanceKey', header: 'Process instance key', isDisabled: true},
  ...COMMON_HEADER_COLUMNS,
];

const DELETE_DECISION_INSTANCE_HEADER_COLUMNS = [
  {
    key: 'decisionInstanceKey',
    header: 'Decision instance key',
    isDisabled: true,
  },
  ...COMMON_HEADER_COLUMNS,
];

const RESOLVE_INCIDENT_HEADER_COLUMNS = [
  {key: 'processInstanceKey', header: 'Process instance key', isDisabled: true},
  {key: 'incidentKey', header: 'Incident key', isDisabled: true},
  ...COMMON_HEADER_COLUMNS,
];

type Props = {
  batchOperationKey: string;
  batchOperationType: BatchOperationType | undefined;
  isLoading: boolean;
};

export const BatchItemsTable: React.FC<Props> = ({
  batchOperationKey,
  batchOperationType,
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

  const headerColumns = (() => {
    if (batchOperationType === 'DELETE_DECISION_INSTANCE') {
      return DELETE_DECISION_INSTANCE_HEADER_COLUMNS;
    }
    if (batchOperationType === 'RESOLVE_INCIDENT') {
      return RESOLVE_INCIDENT_HEADER_COLUMNS;
    }
    return DEFAULT_HEADER_COLUMNS;
  })();

  const rows = useMemo(
    () =>
      items.map(({itemKey, processInstanceKey, state, processedDate}) => {
        const commonCells = {
          id: itemKey.toString(),
          state: <BatchStateIndicator status={state} />,
          processedDate: formatDate(processedDate ?? ''),
        };

        if (batchOperationType === 'DELETE_DECISION_INSTANCE') {
          return {
            ...commonCells,
            decisionInstanceKey: (
              <ItemKeyCell
                itemKey={itemKey}
                fallbackText="No decision instance"
                to={
                  state === 'COMPLETED'
                    ? undefined
                    : Paths.decisionInstance(itemKey)
                }
                label={
                  state === 'COMPLETED'
                    ? undefined
                    : `View decision instance ${itemKey}`
                }
              />
            ),
          };
        }

        if (batchOperationType === 'DELETE_PROCESS_INSTANCE') {
          return {
            ...commonCells,
            processInstanceKey: (
              <ItemKeyCell
                itemKey={processInstanceKey}
                fallbackText="No process instance"
                to={
                  state === 'COMPLETED'
                    ? undefined
                    : Paths.processInstance(processInstanceKey)
                }
                label={
                  state === 'COMPLETED'
                    ? undefined
                    : `View process instance ${processInstanceKey}`
                }
              />
            ),
          };
        }

        if (batchOperationType === 'RESOLVE_INCIDENT') {
          return {
            ...commonCells,
            processInstanceKey: (
              <ItemKeyCell
                itemKey={processInstanceKey}
                fallbackText="No process instance"
                to={Paths.processInstance(processInstanceKey)}
                label={`View process instance ${processInstanceKey}`}
              />
            ),
            incidentKey: (
              <ItemKeyCell itemKey={itemKey} fallbackText="No incident" />
            ),
          };
        }

        return {
          ...commonCells,
          processInstanceKey: (
            <ItemKeyCell
              itemKey={processInstanceKey}
              fallbackText="No process instance"
              to={Paths.processInstance(processInstanceKey)}
              label={`View process instance ${processInstanceKey}`}
            />
          ),
        };
      }),
    [items, batchOperationType],
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
    <TableContainer>
      <BasePanelHeader count={totalItems} title="Items" />
      <PaginatedSortableTable
        size="md"
        batchOperationId={batchOperationKey}
        state={getTableState()}
        rows={rows}
        headerColumns={headerColumns}
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
    </TableContainer>
  );
};
