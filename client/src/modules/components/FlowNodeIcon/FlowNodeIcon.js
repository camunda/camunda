import React from 'react';
import PropTypes from 'prop-types';

import {ACTIVITY_STATE, FLOW_NODE_TYPE} from 'modules/constants';

import {
  FlownodeActivity,
  StateIconFlownodeActivityIncident,
  FlownodeActivityCompleted,
  FlownodeEvent,
  StateIconIncident,
  FlownodeEventCompleted,
  FlownodeGateway,
  StateIconGatewayIncident,
  FlownodeGatewayCompleted,
  Stop
} from 'modules/components/Icon';

const iconsMap = {
  [FLOW_NODE_TYPE.TASK]: {
    [ACTIVITY_STATE.COMPLETED]: FlownodeActivityCompleted,
    [ACTIVITY_STATE.ACTIVE]: FlownodeActivity,
    [ACTIVITY_STATE.INCIDENT]: StateIconFlownodeActivityIncident
  },
  [FLOW_NODE_TYPE.START_EVENT]: {
    [ACTIVITY_STATE.COMPLETED]: FlownodeEventCompleted,
    [ACTIVITY_STATE.ACTIVE]: FlownodeEvent,
    [ACTIVITY_STATE.INCIDENT]: StateIconIncident
  },
  [FLOW_NODE_TYPE.END_EVENT]: {
    [ACTIVITY_STATE.COMPLETED]: FlownodeEventCompleted,
    [ACTIVITY_STATE.ACTIVE]: FlownodeEvent,
    [ACTIVITY_STATE.INCIDENT]: StateIconIncident
  },
  [FLOW_NODE_TYPE.GATEWAY]: {
    [ACTIVITY_STATE.COMPLETED]: FlownodeGatewayCompleted,
    [ACTIVITY_STATE.ACTIVE]: FlownodeGateway,
    [ACTIVITY_STATE.INCIDENT]: StateIconGatewayIncident
  }
};

export default function FlowNodeIcon({state, type, ...props}) {
  const TargetIcon =
    state === ACTIVITY_STATE.TERMINATED ? Stop : iconsMap[type][state];

  return <TargetIcon width="16px" height="16px" {...props} />;
}

FlowNodeIcon.propTypes = {
  state: PropTypes.oneOf(Object.values(ACTIVITY_STATE)),
  type: PropTypes.oneOf(Object.values(FLOW_NODE_TYPE))
};
