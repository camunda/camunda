/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {LinkButton} from 'modules/components/LinkButton';
import {formatDate} from 'modules/utils/date';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {ProgressBar} from './ProgressBar';
import {useNavigate} from 'react-router-dom';
import {Locations} from 'modules/routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {useLoadingProgress} from './useLoadingProgress';
import {
  Cancel,
  Delete,
  Edit,
  EndDate,
  Entry,
  EntryDetails,
  EntryStatus,
  Id,
  InstancesDeletedCount,
  Modify,
  OperationIcon,
  Retry,
  Type,
} from './styled';

type OperationLabelType = 'Edit' | 'Retry' | 'Cancel' | 'Modify' | 'Delete';

const TYPE_LABELS: Readonly<Record<OperationEntityType, OperationLabelType>> = {
  ADD_VARIABLE: 'Edit',
  UPDATE_VARIABLE: 'Edit',
  RESOLVE_INCIDENT: 'Retry',
  CANCEL_PROCESS_INSTANCE: 'Cancel',
  DELETE_PROCESS_INSTANCE: 'Delete',
  MODIFY_PROCESS_INSTANCE: 'Modify',
  DELETE_PROCESS_DEFINITION: 'Delete',
  DELETE_DECISION_DEFINITION: 'Delete',
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
    instancesCount,
    operationsTotalCount,
    operationsFinishedCount,
  } = operation;
  const navigate = useNavigate();

  const {fakeProgressPercentage, isComplete} = useLoadingProgress({
    totalCount: operationsTotalCount,
    finishedCount: operationsFinishedCount,
  });

  function handleInstancesClick(operationId: OperationEntity['id']) {
    panelStatesStore.expandFiltersPanel();

    navigate(
      Locations.processes({
        active: true,
        incidents: true,
        completed: true,
        canceled: true,
        operationId,
      }),
      {state: {hideOptionalFilters: true}}
    );
  }

  const label = TYPE_LABELS[type];

  return (
    <Entry isRunning={!isComplete} data-testid="operations-entry">
      <EntryStatus>
        <div>
          <Type>
            {label}
            {[
              'DELETE_PROCESS_DEFINITION',
              'DELETE_DECISION_DEFINITION',
            ].includes(type)
              ? ` ${name}`
              : ''}
          </Type>
          <Id data-testid="operation-id">{id}</Id>
        </div>
        <OperationIcon>
          {label === 'Retry' && <Retry data-testid="operation-retry-icon" />}
          {label === 'Edit' && <Edit data-testid="operation-edit-icon" />}
          {label === 'Cancel' && <Cancel data-testid="operation-cancel-icon" />}
          {label === 'Delete' && <Delete data-testid="operation-delete-icon" />}
          {label === 'Modify' && <Modify data-testid="operation-modify-icon" />}
        </OperationIcon>
      </EntryStatus>
      {!isComplete && (
        <ProgressBar progressPercentage={fakeProgressPercentage} />
      )}
      <EntryDetails>
        {label !== 'Delete' && (
          <LinkButton onClick={() => handleInstancesClick(id)}>
            {`${pluralSuffix(instancesCount, 'Instance')}`}
          </LinkButton>
        )}

        {['DELETE_PROCESS_DEFINITION', 'DELETE_DECISION_DEFINITION'].includes(
          type
        ) && (
          <InstancesDeletedCount>{`${pluralSuffix(
            operationsFinishedCount,
            'instance'
          )} deleted`}</InstancesDeletedCount>
        )}

        {endDate !== null && isComplete && (
          <EndDate>{formatDate(endDate)}</EndDate>
        )}
      </EntryDetails>
    </Entry>
  );
};

export default OperationsEntry;
