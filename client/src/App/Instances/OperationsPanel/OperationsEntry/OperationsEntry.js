/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {OPERATION_TYPES} from './constants';
import OperationIcon from 'modules/components/OperationIcon';
import {formatDate} from 'modules/utils/date';
import * as Styled from './styled';

const {
  UPDATE_VARIABLE,
  RESOLVE_INCIDENT,
  CANCEL_WORKFLOW_INSTANCE
} = OPERATION_TYPES;

const TYPE_LABELS = {
  [UPDATE_VARIABLE]: 'Edit',
  [RESOLVE_INCIDENT]: 'Retry',
  [CANCEL_WORKFLOW_INSTANCE]: 'Cancel'
};

const OperationsEntry = ({id, type, isRunning, endDate}) => {
  return (
    <Styled.Entry isRunning={isRunning}>
      <Styled.EntryStatus>
        <div>
          <Styled.Type>{TYPE_LABELS[type]}</Styled.Type>
          <Styled.Id>{id}</Styled.Id>
        </div>

        <OperationIcon operationType={type} data-test="operation-icon" />
      </Styled.EntryStatus>
      {endDate && <Styled.EndDate>{formatDate(endDate)}</Styled.EndDate>}
    </Styled.Entry>
  );
};

OperationsEntry.propTypes = {
  id: PropTypes.string.isRequired,
  type: PropTypes.oneOf(Object.values(OPERATION_TYPES)).isRequired,
  endDate: PropTypes.string,
  isRunning: PropTypes.bool.isRequired
};

OperationsEntry.defaultProps = {};

export default OperationsEntry;
