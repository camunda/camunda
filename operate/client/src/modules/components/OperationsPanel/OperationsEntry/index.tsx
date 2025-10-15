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
import {
  Error,
  Tools,
  RetryFailed,
  MigrateAlt,
  TrashCan,
} from '@carbon/react/icons';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import type {
  BatchOperation,
  BatchOperationType,
} from '@camunda/camunda-api-zod-schemas/8.8';

type OperationLabelType = 'Retry' | 'Cancel' | 'Modify' | 'Migrate' | 'Delete';

const TYPE_LABELS: Readonly<Record<BatchOperationType, OperationLabelType>> = {
  RESOLVE_INCIDENT: 'Retry',
  CANCEL_PROCESS_INSTANCE: 'Cancel',
  MODIFY_PROCESS_INSTANCE: 'Modify',
  MIGRATE_PROCESS_INSTANCE: 'Migrate',
  DELETE_PROCESS_INSTANCE: 'Delete',
  DELETE_PROCESS_DEFINITION: 'Delete',
  DELETE_DECISION_DEFINITION: 'Delete',
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
    state,
  } = operation;

  const {fakeProgressPercentage, isComplete} = useLoadingProgress({
    totalCount: operationsTotalCount,
    processedCount: operationsCompletedCount + operationsFailedCount,
    isFinished: endDate !== undefined,
  });

  const label = TYPE_LABELS[batchOperationType] || 'Unknown Operation';

  return (
    <Container data-testid="operations-entry">
      <div>
        <Header>
          <Title>{label}</Title>
          {batchOperationType === 'CANCEL_PROCESS_INSTANCE' && (
            <Error size={16} data-testid="operation-cancel-icon" />
          )}
          {batchOperationType === 'RESOLVE_INCIDENT' && (
            <RetryFailed size={16} data-testid="operation-retry-icon" />
          )}
          {batchOperationType === 'MODIFY_PROCESS_INSTANCE' && (
            <Tools size={16} data-testid="operation-modify-icon" />
          )}
          {batchOperationType === 'MIGRATE_PROCESS_INSTANCE' && (
            <MigrateAlt size={16} data-testid="operation-migrate-icon" />
          )}
          {[
            'DELETE_PROCESS_INSTANCE',
            'DELETE_PROCESS_DEFINITION',
            'DELETE_DECISION_DEFINITION',
          ].includes(batchOperationType) && (
            <TrashCan size={16} data-testid="operation-delete-icon" />
          )}
        </Header>
        {!batchOperationType.startsWith('DELETE') ? (
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
        ) : (
          <span data-testid="operation-id">{batchOperationKey}</span>
        )}
      </div>
      {isComplete ? null : (
        <ProgressBar label="" value={fakeProgressPercentage} />
      )}
      <Details>
        <OperationEntryStatus
          type={batchOperationType}
          failedCount={operationsFailedCount}
          completedCount={operationsCompletedCount}
          state={state}
        />
        {endDate !== undefined && isComplete && (
          <div>{formatDate(endDate)}</div>
        )}
      </Details>
    </Container>
  );
};

export {OperationsEntry};
