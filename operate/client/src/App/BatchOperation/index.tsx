/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect} from 'react';
import {Link, useParams} from 'react-router-dom';
import Breadcrumb from '@carbon/react/lib/components/Breadcrumb/Breadcrumb';
import BreadcrumbItem from '@carbon/react/lib/components/Breadcrumb/BreadcrumbItem';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {Locations, Paths} from 'modules/Routes';
import {useBatchOperation} from 'modules/queries/batch-operations/useBatchOperation';
import {Forbidden} from 'modules/components/Forbidden';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {
  PageContainer,
  ContentContainer,
  PageHeader,
  TilesContainer,
  Tile,
  TileLabel,
} from './styled';
import {BatchStateIndicator} from 'App/BatchOperations/BatchStateIndicator';
import {BatchItemsCount} from 'App/BatchOperations/BatchItemsCount';
import {formatDate} from 'modules/utils/date';
import {SortableTable} from 'modules/components/SortableTable';
import {useBatchOperationItems} from 'modules/queries/batch-operations/useBatchOperationItems';
import {PAGE_TITLE} from 'modules/constants';
import {InlineNotification, SkeletonText} from '@carbon/react';

const BatchOperation: React.FC = () => {
  const {batchOperationKey = ''} = useParams<{batchOperationKey: string}>();
  const {
    data: batchOperationData,
    error,
    isLoading,
  } = useBatchOperation(batchOperationKey);
  const {
    data: batchOperationItemsData,
    status,
    isFetching,
    isFetched,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isFetchingPreviousPage,
  } = useBatchOperationItems({
    filter: {batchOperationKey: batchOperationKey},
    sort: [{field: 'processedDate', order: 'desc'}],
  });

  const {
    batchOperationType,
    state,
    operationsCompletedCount,
    operationsFailedCount,
    operationsTotalCount,
    startDate,
    endDate,
  } = batchOperationData || {};

  const operationType = formatOperationType(batchOperationType ?? '');

  useEffect(() => {
    document.title = PAGE_TITLE.BATCH_OPERATION(operationType);
  }, [operationType]);

  const headers = [
    {
      key: 'processInstanceKey',
      header: 'Process Instance Key',
      isDisabled: true,
    },
    {key: 'state', header: 'Batch state', isDisabled: true},
    {key: 'processedDate', header: 'Time', sortKey: 'processedDate'},
  ];

  const migrationItems =
    batchOperationItemsData?.pages.flatMap((page) => page.items) ?? [];

  const rows = migrationItems.map((item) => ({
    id: item.itemKey.toString(),
    processInstanceKey: item.processInstanceKey,
    state: <BatchStateIndicator status={item.state} />,
    processedDate: formatDate(item.processedDate ?? ''),
  }));

  const totalItems = batchOperationItemsData?.pages[0].page.totalItems;

  const getTableState = () => {
    switch (true) {
      case isFetched && migrationItems.length === 0:
        return 'empty';
      case status === 'pending' && !batchOperationItemsData:
        return 'skeleton';
      case isFetching && !isFetchingPreviousPage && !isFetchingNextPage:
        return 'loading';
      case status === 'error':
        return 'error';
      case status === 'success' && totalItems === 0:
        return 'content';
      default:
        return 'content';
    }
  };

  if (error?.response?.status === HTTP_STATUS_FORBIDDEN) {
    return <Forbidden />;
  }

  const tileData = [
    {
      label: 'State',
      content: state ? <BatchStateIndicator status={state} /> : null,
    },
    {
      label: 'Summary of Items',
      content: (
        <BatchItemsCount
          operationsCompletedCount={operationsCompletedCount ?? 0}
          operationsFailedCount={operationsFailedCount ?? 0}
          operationsTotalCount={operationsTotalCount ?? 0}
        />
      ),
    },
    {
      label: 'Start time',
      content: formatDate(startDate ?? ''),
    },
    {
      label: 'End time',
      content: formatDate(endDate ?? ''),
    },
  ];

  return (
    <PageContainer gap={5}>
      <VisuallyHiddenH1>Batch Operations</VisuallyHiddenH1>
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
          <BreadcrumbItem>
            <Link
              to={Paths.batchOperations()}
              title="View batch operations"
              aria-label="View batch operations"
            >
              Batch Operations
            </Link>
          </BreadcrumbItem>
          <BreadcrumbItem isCurrentPage>
            <div>{operationType}</div>
          </BreadcrumbItem>
        </Breadcrumb>
      </PageHeader>
      <ContentContainer gap={5}>
        <h3>{operationType}</h3>
        {error && (
          <InlineNotification
            kind="error"
            statusIconDescription="notification"
            hideCloseButton
            role="alert"
            title="Failed to load batch operation details"
          />
        )}
        <TilesContainer gap={4} orientation="horizontal">
          {tileData.map(({label, content}) => (
            <Tile key={label}>
              <TileLabel>{label}</TileLabel>
              {isLoading ? <SkeletonText /> : content}
            </Tile>
          ))}
        </TilesContainer>
        <strong>{totalItems} Items</strong>
        <SortableTable
          batchOperationId={batchOperationKey}
          size="md"
          state={getTableState()}
          rowOperationError={(rowId) => {
            const item = migrationItems.find(
              (item) => item.itemKey.toString() === rowId,
            );

            if (item && item.errorMessage) {
              return (
                <>
                  <strong>Failure reason:</strong> {item.errorMessage}
                </>
              );
            }

            return null;
          }}
          rows={rows}
          headerColumns={headers}
          onVerticalScrollEndReach={() => {
            if (hasNextPage && !isFetchingNextPage) {
              fetchNextPage();
            }
          }}
          emptyMessage={{message: 'No items found'}}
          stickyHeader
        />
      </ContentContainer>
    </PageContainer>
  );
};

export {BatchOperation};

const formatOperationType = (type: string) => {
  return type
    .split('_')
    .map((word) => word.charAt(0) + word.slice(1).toLowerCase())
    .join(' ');
};
