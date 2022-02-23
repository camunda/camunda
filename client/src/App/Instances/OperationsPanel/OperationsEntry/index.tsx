/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {LinkButton} from 'modules/components/LinkButton';
import {formatDate} from 'modules/utils/date';
import * as Styled from './styled';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {ProgressBar} from './ProgressBar';
import {useLocation, useNavigate} from 'react-router-dom';
import {Locations} from 'modules/routes';
import {panelStatesStore} from 'modules/stores/panelStates';
import {useLoadingProgress} from './useLoadingProgress';
import {visibleFiltersStore} from 'modules/stores/visibleFilters';

const TYPE_LABELS: Readonly<Record<OperationEntityType, string>> = {
  ADD_VARIABLE: 'Edit',
  UPDATE_VARIABLE: 'Edit',
  RESOLVE_INCIDENT: 'Retry',
  CANCEL_PROCESS_INSTANCE: 'Cancel',
  DELETE_PROCESS_INSTANCE: 'Delete',
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
  const location = useLocation();

  const {fakeProgressPercentage, isComplete} = useLoadingProgress({
    totalCount: operationsTotalCount,
    finishedCount: operationsFinishedCount,
  });

  function handleInstancesClick(operationId: OperationEntity['id']) {
    panelStatesStore.expandFiltersPanel();

    visibleFiltersStore.reset();
    visibleFiltersStore.addVisibleFilters(['operationId']);
    navigate(
      Locations.filters(location, {
        active: true,
        incidents: true,
        completed: true,
        canceled: true,
        operationId,
      })
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
