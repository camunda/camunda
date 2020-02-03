/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {OPERATION_TYPES} from '../constants';
import * as Styled from './styled';

const {RETRY, CANCEL, EDIT} = OPERATION_TYPES;

const TYPE_LABELS = {[RETRY]: 'Retry', [CANCEL]: 'Cancel', [EDIT]: 'Edit'};

const OperationsEntry = ({id, type, isRunning}) => {
  return (
    <Styled.Entry isRunning={isRunning}>
      <Styled.Type>{TYPE_LABELS[type]}</Styled.Type>
      <Styled.Id>{id}</Styled.Id>
    </Styled.Entry>
  );
};

OperationsEntry.propTypes = {
  id: PropTypes.number.isRequired,
  type: PropTypes.oneOf(Object.values(OPERATION_TYPES)).isRequired,
  isRunning: PropTypes.bool.isRequired
};

OperationsEntry.defaultProps = {};

export default OperationsEntry;
