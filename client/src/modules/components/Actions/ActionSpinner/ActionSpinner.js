import React from 'react';

import {OPERATION_STATE} from 'modules/constants';

import * as Styled from './styled';

const isOperationScheduled = operationState => {
  const scheduledState = [OPERATION_STATE.SCHEDULED, OPERATION_STATE.LOCKED];
  return scheduledState.includes(operationState);
};

const ActionSpinner = ({operationState, selected}) =>
  isOperationScheduled(operationState) && (
    <Styled.ActionSpinner selected={selected} />
  );

export default ActionSpinner;
