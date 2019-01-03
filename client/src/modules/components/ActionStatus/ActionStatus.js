import React from 'react';
import PropTypes from 'prop-types';

import {OPERATION_STATE} from 'modules/constants';
import {OPERATION_TYPE} from 'modules/constants';

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

const getTitle = (instanceId, type) => {
  const titleMap = {
    [OPERATION_TYPE.UPDATE_RETRIES]: `Retrying Instance ${instanceId} failed`,
    [OPERATION_TYPE.CANCEL]: `Canceling Instance ${instanceId} failed`,
    SCHEDULED: `Instance ${instanceId} has scheduled Operations`
  };
  return titleMap[type];
};

const isOperationFailed = operationState =>
  operationState === OPERATION_STATE.FAILED;

const ActionStatus = ({
  operationState,
  operationType,
  selected,
  instance,
  ...props
}) => {
  const isScheduled = isOperationScheduled(operationState);
  const isFailed = isOperationFailed(operationState);

  if (!(isScheduled || isFailed)) {
    return null;
  }

  return (
    <div>
      {isScheduled && (
        <Styled.ActionSpinner
          selected={selected}
          title={getTitle(instance.id, 'SCHEDULED')}
          {...props}
        />
      )}
      {isFailed && (
        <StatusItems {...props}>
          <StatusItems.Item
            type={operationType}
            title={getTitle(instance.id, operationType)}
          />
        </StatusItems>
      )}
    </div>
  );
};

export default ActionStatus;

ActionStatus.propTypes = {
  operationState: PropTypes.string,
  operationType: PropTypes.string,
  selected: PropTypes.bool,
  instance: PropTypes.object
};
