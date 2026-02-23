/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useEffect} from 'react';
import {Link, useNavigate, useParams} from 'react-router-dom';
import {
  Breadcrumb,
  BreadcrumbItem,
  InlineNotification,
  SkeletonText,
} from '@carbon/react';
import {formatDate} from 'modules/utils/date';
import {PAGE_TITLE} from 'modules/constants';
import {Locations, Paths} from 'modules/Routes';
import {Forbidden} from 'modules/components/Forbidden';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {useBatchOperation} from 'modules/queries/batch-operations/useBatchOperation';
import {BatchItemsCount} from 'App/BatchOperations/BatchItemsCount';
import {formatOperationType} from 'modules/utils/formatOperationType';
import {BatchStateIndicator} from 'App/BatchOperations/BatchStateIndicator';
import {tracking} from 'modules/tracking';
import {
  PageContainer,
  ContentContainer,
  BreadcrumbBar,
  TilesContainer,
  Tile,
  TileLabel,
  Header,
} from './styled';
import {notificationsStore} from 'modules/stores/notifications';
import {BatchItemsTable} from './BatchItemsTable';
import {OperationsActions} from './OperationActions';

const renderWithLoading = (isLoading: boolean, content: React.ReactNode) =>
  isLoading ? <SkeletonText data-testid="text-skeleton" /> : content;

const BatchOperation: React.FC = () => {
  const navigate = useNavigate();
  const {batchOperationKey = ''} = useParams<{batchOperationKey: string}>();
  const {
    data: batchOperationData,
    error,
    isLoading,
  } = useBatchOperation(batchOperationKey);

  const {
    batchOperationType,
    state,
    operationsCompletedCount,
    operationsFailedCount,
    operationsTotalCount,
    startDate,
    endDate,
    actorId,
  } = batchOperationData || {};

  const operationType = formatOperationType(batchOperationType ?? '');

  useEffect(() => {
    document.title = PAGE_TITLE.BATCH_OPERATION(operationType);
  }, [operationType]);

  useEffect(() => {
    if (batchOperationData) {
      const {batchOperationType, state, operationsTotalCount} =
        batchOperationData;
      tracking.track({
        eventName: 'batch-operation-details-loaded',
        batchOperationType,
        batchOperationState: state,
        operationsTotalCount,
      });
    }
  }, [batchOperationData]);

  useEffect(() => {
    if (error?.response?.status === 404 && batchOperationKey) {
      notificationsStore.displayNotification({
        kind: 'error',
        title: `Batch operation ${batchOperationKey} could not be found`,
        isDismissable: true,
      });
      navigate(Paths.batchOperations(), {replace: true});
    }
  }, [error, batchOperationKey, navigate]);

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
    {
      label: 'Actor',
      content: actorId ?? '--',
    },
  ];

  return (
    <PageContainer gap={5}>
      <VisuallyHiddenH1>Batch Operations</VisuallyHiddenH1>
      <BreadcrumbBar>
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
            <div>{renderWithLoading(isLoading, operationType)}</div>
          </BreadcrumbItem>
        </Breadcrumb>
      </BreadcrumbBar>
      <ContentContainer gap={5}>
        {renderWithLoading(
          isLoading,
          <Header>
            <h3>{operationType}</h3>
            {batchOperationData && (
              <OperationsActions
                batchOperationKey={batchOperationKey}
                batchOperationState={batchOperationData.state}
                batchOperationType={batchOperationData.batchOperationType}
              />
            )}
          </Header>,
        )}
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
              {renderWithLoading(isLoading, content)}
            </Tile>
          ))}
        </TilesContainer>
        <BatchItemsTable
          batchOperationKey={batchOperationKey}
          isLoading={isLoading}
        />
      </ContentContainer>
    </PageContainer>
  );
};

export {BatchOperation};
