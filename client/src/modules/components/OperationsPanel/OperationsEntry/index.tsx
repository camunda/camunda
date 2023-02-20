/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {LinkButton} from 'modules/components/LinkButton';
import {formatDate} from 'modules/utils/date';
import * as Styled from './styled';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {ProgressBar} from './ProgressBar';
import {useNavigate} from 'react-router-dom';
import {Locations} from 'modules/routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {useLoadingProgress} from './useLoadingProgress';

const TYPE_LABELS: Readonly<Record<OperationEntityType, string>> = {
  ADD_VARIABLE: 'Edit',
  UPDATE_VARIABLE: 'Edit',
  RESOLVE_INCIDENT: 'Retry',
  CANCEL_PROCESS_INSTANCE: 'Cancel',
  DELETE_PROCESS_INSTANCE: 'Delete',
  MODIFY_PROCESS_INSTANCE: 'Modify',
};

type Props = {
  operation: OperationEntity;
};

const OperationsEntry: React.FC<Props> = ({operation}) => {
  const {
    id,
    type,
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

  return (
    <Styled.Entry isRunning={!isComplete} data-testid="operations-entry">
      <Styled.EntryStatus>
        <div>
          <Styled.Type>{TYPE_LABELS[type]}</Styled.Type>
          <Styled.Id data-testid="operation-id">{id}</Styled.Id>
        </div>
        <Styled.OperationIcon>
          {'RESOLVE_INCIDENT' === type && (
            <Styled.Retry data-testid="operation-retry-icon" />
          )}
          {('UPDATE_VARIABLE' === type || 'ADD_VARIABLE' === type) && (
            <Styled.Edit data-testid="operation-edit-icon" />
          )}
          {'CANCEL_PROCESS_INSTANCE' === type && (
            <Styled.Cancel data-testid="operation-cancel-icon" />
          )}
          {'DELETE_PROCESS_INSTANCE' === type && (
            <Styled.Delete data-testid="operation-delete-icon" />
          )}
          {'MODIFY_PROCESS_INSTANCE' === type && (
            <Styled.Modify data-testid="operation-modify-icon" />
          )}
        </Styled.OperationIcon>
      </Styled.EntryStatus>
      {!isComplete && (
        <ProgressBar progressPercentage={fakeProgressPercentage} />
      )}
      <Styled.EntryDetails>
        {'DELETE_PROCESS_INSTANCE' !== type && (
          <LinkButton onClick={() => handleInstancesClick(id)}>
            {`${pluralSuffix(instancesCount, 'Instance')}`}
          </LinkButton>
        )}
        {endDate !== null && isComplete && (
          <Styled.EndDate>{formatDate(endDate)}</Styled.EndDate>
        )}
      </Styled.EntryDetails>
    </Styled.Entry>
  );
};

export default OperationsEntry;
