import React from 'react';
import PropTypes from 'prop-types';

import {OPERATION_STATE} from 'modules/constants';

import StatusItems from './StatusItems';

import * as Styled from './styled';

const isOperationScheduled = operationState => {
  const scheduledState = [
    OPERATION_STATE.SCHEDULED,
    OPERATION_STATE.LOCKED,
    OPERATION_STATE.SENT
  ];
  return scheduledState.includes(operationState);
};

const isOperationFailed = operationState =>
  operationState === OPERATION_STATE.FAILED;

const ActionStatus = ({operationState, operationType, selected}) => {
  const isScheduled = isOperationScheduled(operationState);
  const isFailed = isOperationFailed(operationState);

  if (!(isScheduled || isFailed)) {
    return null;
  }

  return (
    <div>
      {isScheduled && <Styled.ActionSpinner selected={selected} />}
      {isFailed && (
        <StatusItems>
          <StatusItems.Item type={operationType} />
        </StatusItems>
      )}
    </div>
  );
};

export default ActionStatus;

ActionStatus.propTypes = {
  operationState: PropTypes.string,
  operationType: PropTypes.string,
  selected: PropTypes.bool
};
