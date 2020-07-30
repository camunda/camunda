/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {LinkButton} from 'modules/components/LinkButton';
import {OPERATION_TYPE} from 'modules/constants';
import {formatDate} from 'modules/utils/date';
import * as Styled from './styled';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {isBatchOperationRunning} from '../service';
import ProgressBar from './ProgressBar';

const {
  UPDATE_VARIABLE,
  RESOLVE_INCIDENT,
  CANCEL_WORKFLOW_INSTANCE,
} = OPERATION_TYPE;

const TYPE_LABELS = {
  [UPDATE_VARIABLE]: 'Edit',
  [RESOLVE_INCIDENT]: 'Retry',
  [CANCEL_WORKFLOW_INSTANCE]: 'Cancel',
};

const OperationsEntry = ({batchOperation, onInstancesClick}) => {
  const {
    id,
    type,
    endDate,
    instancesCount,
    operationsTotalCount,
    operationsFinishedCount,
  } = batchOperation;
  return (
    <Styled.Entry isRunning={isBatchOperationRunning(batchOperation)}>
      <Styled.EntryStatus>
        <div>
          <Styled.Type>{TYPE_LABELS[type]}</Styled.Type>
          <Styled.Id data-test="operation-id">{id}</Styled.Id>
        </div>
        <Styled.OperationIcon>
          {RESOLVE_INCIDENT === type && (
            <Styled.Retry data-test="operation-retry-icon" />
          )}
          {UPDATE_VARIABLE === type && (
            <Styled.Edit data-test="operation-edit-icon" />
          )}
          {CANCEL_WORKFLOW_INSTANCE === type && (
            <Styled.Cancel data-test="operation-cancel-icon" />
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
        <LinkButton onClick={() => onInstancesClick(id)}>{`${pluralSuffix(
          instancesCount,
          'Instance'
        )}`}</LinkButton>
        {endDate && <Styled.EndDate>{formatDate(endDate)}</Styled.EndDate>}
      </Styled.EntryDetails>
    </Styled.Entry>
  );
};

OperationsEntry.propTypes = {
  batchOperation: PropTypes.shape({
    id: PropTypes.string.isRequired,
    type: PropTypes.oneOf(Object.values(OPERATION_TYPE)).isRequired,
    endDate: PropTypes.string,
    instancesCount: PropTypes.number.isRequired,
    operationsTotalCount: PropTypes.number.isRequired,
    operationsFinishedCount: PropTypes.number.isRequired,
  }).isRequired,
  onInstancesClick: PropTypes.func.isRequired,
};

OperationsEntry.defaultProps = {};

export default OperationsEntry;
