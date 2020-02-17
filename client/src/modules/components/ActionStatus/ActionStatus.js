/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {ACTIVE_OPERATION_STATES, OPERATION_STATE} from 'modules/constants';
import {OPERATION_TYPE} from 'modules/constants';

import StatusItems from './StatusItems';

import * as Styled from './styled';

const getTitleByOperationType = (instanceId, type) => {
  switch (type) {
    case OPERATION_TYPE.RESOLVE_INCIDENT:
      return `Retrying Instance ${instanceId} failed`;
    case OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE:
      return `Canceling Instance ${instanceId} failed`;
    default:
      return '';
  }
};

const ActionStatus = ({
  operationState,
  operationType,
  selected,
  instance,
  forceSpinner,
  ...props
}) => {
  if (forceSpinner || ACTIVE_OPERATION_STATES.includes(operationState)) {
    return (
      <Styled.ActionSpinner
        selected={selected}
        title={`Instance ${instance.id} has scheduled Operations`}
        {...props}
      />
    );
  }

  if (operationState === OPERATION_STATE.FAILED) {
    return (
      <StatusItems {...props}>
        <StatusItems.Item
          type={operationType}
          title={getTitleByOperationType(instance.id, operationType)}
        />
      </StatusItems>
    );
  }

  return null;
};

ActionStatus.Spinner = Styled.ActionSpinner;
export default ActionStatus;

ActionStatus.propTypes = {
  operationState: PropTypes.string,
  operationType: PropTypes.string,
  selected: PropTypes.bool,
  instance: PropTypes.object,
  forceSpinner: PropTypes.bool
};
