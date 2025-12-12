/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useEffect, useState} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {Link, Breadcrumb, BreadcrumbItem, Pagination} from '@carbon/react';
import {Locations} from 'modules/Routes';
import {PAGE_TITLE} from 'modules/constants';
import {formatDate} from 'modules/utils/date';
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
import {parseBatchOperationsSearchSort} from 'modules/utils/filter/v2/batchOperationsSearchSort';
import {BatchItemsCount} from './BatchItemsCount';
import {BatchStateIndicator} from './BatchStateIndicator';

const MAX_OPERATIONS_PER_REQUEST = 20;

const BatchOperations: React.FC = () => {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(MAX_OPERATIONS_PER_REQUEST);
  const sort = parseBatchOperationsSearchSort(params);

  useEffect(() => {
    document.title = PAGE_TITLE.BATCH_OPERATIONS;
  }, []);

  const headers = [
    {key: 'operationType', header: 'Operation', sortKey: 'operationType'},
    {key: 'state', header: 'Batch state', sortKey: 'state'},
    {key: 'items', header: 'Items', isDisabled: true},
    {key: 'startDate', header: 'Start date', sortKey: 'startDate'},
  ];

  const {data, isError, isLoading, isFetched, isFetching} =
    usePaginatedBatchOperations(
      {sort, page: {limit: pageSize, from: (page - 1) * pageSize}},
      {page, pageSize},
    );

  const operations = useMemo(() => data?.items ?? [], [data]);

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
    }) => {
      return {
        id: batchOperationKey,
        operationType: formatOperationType(batchOperationType),
        state: <BatchStateIndicator status={state} />,
        items: (
          <BatchItemsCount
            operationsCompletedCount={operationsCompletedCount}
            operationsFailedCount={operationsFailedCount}
            operationsTotalCount={operationsTotalCount}
          />
        ),
        startTime: formatDate(startDate ?? ''),
      };
    },
  );

  return (
    <>
      <VisuallyHiddenH1>Batch Operations</VisuallyHiddenH1>
      <PageContainer>
        <PageHeader>
          <Breadcrumb noTrailingSlash>
            <BreadcrumbItem>
              <Link
                href="#"
                onClick={(e) => {
                  e.preventDefault();
                  navigate(Locations.processes());
                }}
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
              emptyMessage={{
                message: 'No batch operations found',
                additionalInfo:
                  'Try adjusting your filters or check back later.',
              }}
            />
          </TableContainer>
          {operations.length > 0 && (
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
              totalItems={data?.page.totalItems ?? 0}
              onChange={({page, pageSize}) => {
                setPage(page);
                setPageSize(pageSize);
              }}
            />
          )}
        </PageWrapper>
      </PageContainer>
    </>
  );
};

export {BatchOperations};

const formatOperationType = (type: string) => {
  return type
    .split('_')
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(' ');
};
