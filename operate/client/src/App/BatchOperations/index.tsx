/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useEffect} from 'react';
import {useSearchParams, Link} from 'react-router-dom';
import {Breadcrumb, BreadcrumbItem, Pagination} from '@carbon/react';
import {Locations} from 'modules/Routes';
import {PAGE_TITLE} from 'modules/constants';
import {tracking} from 'modules/tracking';
import {formatDate} from 'modules/utils/date';
import {Forbidden} from 'modules/components/Forbidden';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {SortableTable} from 'modules/components/SortableTable';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {usePaginatedBatchOperations} from 'modules/queries/batch-operations/usePaginatedBatchOperations';
import {
  PageContainer,
  PageHeader,
  PageWrapper,
  PanelHeader,
  TableContainer,
} from './styled';
import {parseBatchOperationsSearchSort} from 'modules/utils/filter/batchOperationsSearchSort';
import {BatchItemsCount} from './BatchItemsCount';
import {BatchStateIndicator} from './BatchStateIndicator';
import {formatOperationType} from 'modules/utils/formatOperationType';

const MAX_OPERATIONS_PER_REQUEST = 20;

const BatchOperations: React.FC = () => {
  const [params, setParams] = useSearchParams();
  const page = parseInt(params.get('page') ?? '1', 10);
  const pageSize = parseInt(
    params.get('pageSize') ?? String(MAX_OPERATIONS_PER_REQUEST),
    10,
  );
  const sort = parseBatchOperationsSearchSort(params);

  useEffect(() => {
    document.title = PAGE_TITLE.BATCH_OPERATIONS;
  }, []);

  const headers = [
    {key: 'operationType', header: 'Operation', sortKey: 'operationType'},
    {key: 'state', header: 'Batch state', sortKey: 'state'},
    {key: 'items', header: 'Items', isDisabled: true},
    {key: 'actor', header: 'Actor', sortKey: 'actorId'},
    {key: 'startDate', header: 'Start date', sortKey: 'startDate'},
  ];

  const {data, error, isError, isLoading, isFetched, isFetching} =
    usePaginatedBatchOperations({sort}, {page, pageSize});

  const operations = useMemo(() => data?.items ?? [], [data]);
  const totalItems = data?.page.totalItems ?? 0;

  const getTableState = () => {
    if (isLoading) {
      return 'skeleton';
    }
    if (isFetching) {
      return 'loading';
    }
    if (isError) {
      return 'error';
    }
    if (isFetched && operations.length === 0) {
      return 'empty';
    }

    return 'content';
  };

  const rows = operations.map(
    ({
      batchOperationKey,
      batchOperationType,
      state,
      operationsCompletedCount,
      operationsFailedCount,
      operationsTotalCount,
      startDate,
      actorId,
    }) => {
      return {
        id: batchOperationKey,
        operationType: (
          <Link
            to={batchOperationKey}
            onClick={() => {
              tracking.track({
                eventName: 'batch-operation-details-opened',
                batchOperationType,
                batchOperationState: state,
              });
            }}
          >
            {formatOperationType(batchOperationType)}
          </Link>
        ),
        state: <BatchStateIndicator status={state} />,
        items: (
          <BatchItemsCount
            operationsCompletedCount={operationsCompletedCount}
            operationsFailedCount={operationsFailedCount}
            operationsTotalCount={operationsTotalCount}
          />
        ),
        actor: actorId ?? '--',
        startDate: formatDate(startDate ?? ''),
      };
    },
  );

  if (error?.response?.status === HTTP_STATUS_FORBIDDEN) {
    return <Forbidden />;
  }

  return (
    <>
      <VisuallyHiddenH1>Batch Operations</VisuallyHiddenH1>
      <PageContainer>
        <PageHeader>
          <Breadcrumb noTrailingSlash>
            <BreadcrumbItem>
              <Link
                to={Locations.processes()}
                title="View processes"
                aria-label="View processes"
              >
                Processes
              </Link>
            </BreadcrumbItem>
            <BreadcrumbItem isCurrentPage>Batch Operations</BreadcrumbItem>
          </Breadcrumb>
        </PageHeader>
        <PageWrapper>
          <PanelHeader>
            <h3>Batch Operations</h3>
          </PanelHeader>
          <TableContainer>
            <SortableTable
              size="md"
              state={getTableState()}
              headerColumns={headers}
              rows={rows}
              onSort={(sortKey) => {
                const previousOrder = sort.find(
                  (s) => s.field === sortKey,
                )?.order;

                tracking.track({
                  eventName: 'batch-operations-sorted',
                  sortBy: sortKey,
                  sortOrder: previousOrder === 'desc' ? 'asc' : 'desc',
                });
              }}
              emptyMessage={{
                message: 'No batch operations found',
                additionalInfo:
                  'Try adjusting your filters or check back later.',
              }}
              stickyHeader
            />
          </TableContainer>
          {totalItems > pageSize && (
            <Pagination
              data-testid="batch-operations-pagination"
              backwardText="Previous page"
              forwardText="Next page"
              itemsPerPageText="Items per page:"
              pageNumberText="Page Number"
              size="md"
              page={page}
              pageSize={pageSize}
              pageSizes={[20, 50, 100]}
              totalItems={totalItems}
              onChange={({page, pageSize}) => {
                setParams((currentParams) => {
                  const newParams = new URLSearchParams(currentParams);
                  newParams.set('page', String(page));
                  newParams.set('pageSize', String(pageSize));
                  return newParams;
                });
              }}
            />
          )}
        </PageWrapper>
      </PageContainer>
    </>
  );
};

export {BatchOperations};
