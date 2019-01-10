import React from 'react';
import PropTypes from 'prop-types';

import {OPERATION_STATE} from 'modules/constants';
import {OPERATION_TYPE} from 'modules/constants';

import StatusItems from './StatusItems';

import * as Styled from './styled';

const SCHEDULED_STATES = [
  OPERATION_STATE.SCHEDULED,
  OPERATION_STATE.LOCKED,
  OPERATION_STATE.SENT
];

const getTitleByOperationType = (instanceId, type) => {
  switch (type) {
    case OPERATION_TYPE.UPDATE_RETRIES:
      return `Retrying Instance ${instanceId} failed`;
    case OPERATION_TYPE.CANCEL:
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
  ...props
}) => {
  if (SCHEDULED_STATES.includes(operationState)) {
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

export default ActionStatus;

ActionStatus.propTypes = {
  operationState: PropTypes.string,
  operationType: PropTypes.string,
  selected: PropTypes.bool,
  instance: PropTypes.object
};
