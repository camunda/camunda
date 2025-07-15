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
import OperationEntryStatus from './OperationEntryStatus';
import {
  TrashCan,
  Error,
  Tools,
  RetryFailed,
  Edit,
  MigrateAlt,
  Move,
} from '@carbon/react/icons';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import type {OperationEntity, OperationEntityType} from 'modules/types/operate';

type OperationLabelType =
  | 'Edit'
  | 'Retry'
  | 'Cancel'
  | 'Modify'
  | 'Delete'
  | 'Migrate'
  | 'Batch Modification';

const TYPE_LABELS: Readonly<Record<OperationEntityType, OperationLabelType>> = {
  ADD_VARIABLE: 'Edit',
  UPDATE_VARIABLE: 'Edit',
  RESOLVE_INCIDENT: 'Retry',
  CANCEL_PROCESS_INSTANCE: 'Cancel',
  DELETE_PROCESS_INSTANCE: 'Delete',
  MODIFY_PROCESS_INSTANCE: 'Modify',
  DELETE_PROCESS_DEFINITION: 'Delete',
  DELETE_DECISION_DEFINITION: 'Delete',
  MIGRATE_PROCESS_INSTANCE: 'Migrate',
  MOVE_TOKEN: 'Batch Modification',
};

type Props = {
  operation: OperationEntity;
};

const OperationsEntry: React.FC<Props> = ({operation}) => {
  const {
    id,
    type,
    name,
    endDate,
    operationsTotalCount,
    operationsFinishedCount,
    failedOperationsCount,
    completedOperationsCount,
  } = operation;

  const {fakeProgressPercentage, isComplete} = useLoadingProgress({
    totalCount: operationsTotalCount,
    finishedCount: operationsFinishedCount,
    isFinished: endDate !== null,
  });

  const label = TYPE_LABELS[type];

  const isTypeDeleteProcessOrDecision = [
    'DELETE_PROCESS_DEFINITION',
    'DELETE_DECISION_DEFINITION',
  ].includes(type);

  const shouldHaveIdLink =
    label !== 'Delete' ||
    (isTypeDeleteProcessOrDecision && failedOperationsCount) ||
    (label === 'Delete' &&
      !isTypeDeleteProcessOrDecision &&
      failedOperationsCount);

  return (
    <Container data-testid="operations-entry">
      <Header>
        <Title>
          {label}
          {isTypeDeleteProcessOrDecision ? ` ${name}` : ''}
        </Title>
        {label === 'Delete' && (
          <TrashCan size={16} data-testid="operation-delete-icon" />
        )}
        {label === 'Cancel' && (
          <Error size={16} data-testid="operation-cancel-icon" />
        )}
        {label === 'Retry' && (
          <RetryFailed size={16} data-testid="operation-retry-icon" />
        )}
        {label === 'Modify' && (
          <Tools size={16} data-testid="operation-modify-icon" />
        )}
        {label === 'Edit' && (
          <Edit size={16} data-testid="operation-edit-icon" />
        )}
        {label === 'Migrate' && (
          <MigrateAlt size={16} data-testid="operation-migrate-icon" />
        )}
        {label === 'Batch Modification' && (
          <Move size={16} data-testid="operation-move-icon" />
        )}
      </Header>
      {shouldHaveIdLink ? (
        <Link
          data-testid="operation-id"
          to={{
            pathname: Paths.processes(),
            search: `?active=true&incidents=true&completed=true&canceled=true&operationId=${id}`,
          }}
          state={{hideOptionalFilters: true}}
          onClick={panelStatesStore.expandFiltersPanel}
        >
          {id}
        </Link>
      ) : (
        <div data-testid="operation-id">{id}</div>
      )}
      {!isComplete && <ProgressBar label="" value={fakeProgressPercentage} />}
      <Details>
        <OperationEntryStatus
          isTypeDeleteProcessOrDecision={isTypeDeleteProcessOrDecision}
          label={label}
          failedOperationsCount={failedOperationsCount}
          completedOperationsCount={completedOperationsCount}
        />

        {endDate !== null && isComplete && <div>{formatDate(endDate)}</div>}
      </Details>
    </Container>
  );
};

export default OperationsEntry;
export type {OperationLabelType};
