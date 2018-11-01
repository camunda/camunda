import React from 'react';
import PropTypes from 'prop-types';

import {OPERATION_STATE} from 'modules/constants';

import StatusItems from '../StatusItems';

import * as Styled from './styled';

const isOperationScheduled = operationState => {
  const scheduledState = [OPERATION_STATE.SCHEDULED, OPERATION_STATE.LOCKED];
  return scheduledState.includes(operationState);
};

const isOperationFailed = operationState =>
  operationState === OPERATION_STATE.FAILED;

const renderActionSpinner = (operationState, selected) =>
  isOperationScheduled(operationState) && (
    <Styled.ActionSpinner selected={selected} />
  );

const renderFailedStatus = (operationState, operationType) =>
  isOperationFailed(operationState) && (
    <StatusItems>
      <StatusItems.Item type={operationType} />
    </StatusItems>
  );

const ActionStatus = ({operationState, operationType, selected}) => (
  <div>
    {renderActionSpinner(operationState, selected)}
    {renderFailedStatus(operationState, operationType)}
  </div>
);

export default ActionStatus;

ActionStatus.propTypes = {
  operationState: PropTypes.string,
  operationType: PropTypes.string,
  selected: PropTypes.bool
};
