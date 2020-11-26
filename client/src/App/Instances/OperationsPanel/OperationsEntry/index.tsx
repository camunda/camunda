/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {LinkButton} from 'modules/components/LinkButton';
import {
  OPERATION_TYPE,
  DEFAULT_FILTER_CONTROLLED_VALUES,
} from 'modules/constants';
import {OperationType} from 'modules/types';
import {formatDate} from 'modules/utils/date';
import * as Styled from './styled';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {isOperationRunning} from '../service';
import ProgressBar from './ProgressBar';
import {filtersStore} from 'modules/stores/filters';

const {
  UPDATE_VARIABLE,
  RESOLVE_INCIDENT,
  CANCEL_WORKFLOW_INSTANCE,
} = OPERATION_TYPE;

const TYPE_LABELS = {
  [UPDATE_VARIABLE]: 'Edit',
  [RESOLVE_INCIDENT]: 'Retry',
  [CANCEL_WORKFLOW_INSTANCE]: 'Cancel',
} as const;

type Props = {
  operation: {
    id: string;
    type: OperationType;
    endDate?: string;
    instancesCount: number;
    operationsTotalCount: number;
    operationsFinishedCount: number;
  };
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

  const handleInstancesClick = (batchOperationId: any) => {
    filtersStore.setFilter({
      ...DEFAULT_FILTER_CONTROLLED_VALUES,
      active: true,
      incidents: true,
      completed: true,
      canceled: true,
      batchOperationId,
    });
  };

  return (
    <Styled.Entry
      isRunning={isOperationRunning(operation)}
      data-testid="operations-entry"
    >
      <Styled.EntryStatus>
        <div>
          <Styled.Type>{TYPE_LABELS[type]}</Styled.Type>
          <Styled.Id data-testid="operation-id">{id}</Styled.Id>
        </div>
        <Styled.OperationIcon>
          {RESOLVE_INCIDENT === type && (
            <Styled.Retry data-testid="operation-retry-icon" />
          )}
          {UPDATE_VARIABLE === type && (
            <Styled.Edit data-testid="operation-edit-icon" />
          )}
          {CANCEL_WORKFLOW_INSTANCE === type && (
            <Styled.Cancel data-testid="operation-cancel-icon" />
          )}
        </Styled.OperationIcon>
      </Styled.EntryStatus>
      {!endDate && (
        <ProgressBar
          totalCount={operationsTotalCount}
          finishedCount={operationsFinishedCount}
        />
      )}
      <Styled.EntryDetails>
        <LinkButton onClick={() => handleInstancesClick(id)}>{`${pluralSuffix(
          instancesCount,
          'Instance'
        )}`}</LinkButton>
        {endDate && <Styled.EndDate>{formatDate(endDate)}</Styled.EndDate>}
      </Styled.EntryDetails>
    </Styled.Entry>
  );
};

export default OperationsEntry;
