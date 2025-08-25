/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatDate} from 'modules/utils/date';
import {useLoadingProgress} from './useLoadingProgress';
import {Container, Details, Title, Header, ProgressBar} from './styled';
import {OperationEntryStatus} from './OperationEntryStatus';
import {Error, Tools, RetryFailed, MigrateAlt} from '@carbon/react/icons';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import type {
  BatchOperation,
  BatchOperationType,
} from '@vzeta/camunda-api-zod-schemas/8.8';

type OperationLabelType = 'Retry' | 'Cancel' | 'Modify' | 'Migrate';

const TYPE_LABELS: Readonly<Record<BatchOperationType, OperationLabelType>> = {
  RESOLVE_INCIDENT: 'Retry',
  CANCEL_PROCESS_INSTANCE: 'Cancel',
  MODIFY_PROCESS_INSTANCE: 'Modify',
  MIGRATE_PROCESS_INSTANCE: 'Migrate',
};

type Props = {
  operation: BatchOperation;
};

const OperationsEntry: React.FC<Props> = ({operation}) => {
  const {
    batchOperationKey,
    batchOperationType,
    endDate,
    operationsTotalCount,
    operationsCompletedCount,
    operationsFailedCount,
  } = operation;

  const {fakeProgressPercentage, isComplete} = useLoadingProgress({
    totalCount: operationsTotalCount,
    processedCount: operationsCompletedCount + operationsFailedCount,
    isFinished: endDate !== undefined,
  });

  const label = TYPE_LABELS[batchOperationType];

  return (
    <Container data-testid="operations-entry">
      <Header>
        <Title>{label}</Title>
        {label === 'Cancel' && (
          <Error size={16} data-testid="operation-cancel-icon" />
        )}
        {label === 'Retry' && (
          <RetryFailed size={16} data-testid="operation-retry-icon" />
        )}
        {label === 'Modify' && (
          <Tools size={16} data-testid="operation-modify-icon" />
        )}
        {label === 'Migrate' && (
          <MigrateAlt size={16} data-testid="operation-migrate-icon" />
        )}
      </Header>
      <Link
        data-testid="operation-id"
        to={{
          pathname: Paths.processes(),
          search: `?active=true&incidents=true&completed=true&canceled=true&operationId=${batchOperationKey}`,
        }}
        state={{hideOptionalFilters: true}}
        onClick={panelStatesStore.expandFiltersPanel}
      >
        {batchOperationKey}
      </Link>
      {!isComplete && <ProgressBar label="" value={fakeProgressPercentage} />}
      <Details>
        <OperationEntryStatus
          type={batchOperationType}
          failedCount={operationsFailedCount}
          completedCount={operationsCompletedCount}
        />

        {endDate !== undefined && isComplete && (
          <div>{formatDate(endDate)}</div>
        )}
      </Details>
    </Container>
  );
};

export {OperationsEntry};
