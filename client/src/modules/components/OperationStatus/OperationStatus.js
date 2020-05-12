/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {ACTIVE_OPERATION_STATES} from 'modules/constants';

import * as Styled from './styled';

const OperationStatus = ({
  operationState,
  operationType,
  selected,
  instance,
  forceSpinner,
  ...props
}) => {
  if (forceSpinner || ACTIVE_OPERATION_STATES.includes(operationState)) {
    return (
      <Styled.OperationSpinner
        selected={selected}
        title={`Instance ${instance.id} has scheduled Operations`}
        data-test="operation-spinner"
        {...props}
      />
    );
  }

  return null;
};

OperationStatus.Spinner = Styled.OperationSpinner;
export default OperationStatus;

OperationStatus.propTypes = {
  operationState: PropTypes.string,
  operationType: PropTypes.string,
  selected: PropTypes.bool,
  instance: PropTypes.object,
  forceSpinner: PropTypes.bool,
};
