import React from 'react';
import PropTypes from 'prop-types';

import * as Styled from './styled';

import {ACTIVITY_STATE, ACTIVITY_TYPE} from 'modules/constants';
import {themed} from 'modules/theme';

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
  InstanceHistoryIconCancelDark,
  InstanceHistoryIconCancelLight
} from 'modules/components/Icon';

const iconsMap = {
  [ACTIVITY_TYPE.TASK]: {
    [ACTIVITY_STATE.COMPLETED]: FlownodeActivityCompleted,
    [ACTIVITY_STATE.ACTIVE]: FlownodeActivity,
    [ACTIVITY_STATE.INCIDENT]: StateIconFlownodeActivityIncident
  },
  [ACTIVITY_TYPE.EVENT]: {
    [ACTIVITY_STATE.COMPLETED]: FlownodeEventCompleted,
    [ACTIVITY_STATE.ACTIVE]: FlownodeEvent,
    [ACTIVITY_STATE.INCIDENT]: StateIconIncident
  },
  [ACTIVITY_TYPE.GATEWAT]: {
    [ACTIVITY_STATE.COMPLETED]: FlownodeGateway,
    [ACTIVITY_STATE.ACTIVE]: StateIconGatewayIncident,
    [ACTIVITY_STATE.INCIDENT]: FlownodeGatewayCompleted
  }
};

function FlowNodeIcon({state, type, ...props}) {
  if (state === ACTIVITY_STATE.TERMINATED) {
    const TargetIcon =
      props.theme === 'dark'
        ? InstanceHistoryIconCancelDark
        : InstanceHistoryIconCancelLight;

    return <TargetIcon width="20px" height="20px" {...props} />;
  }

  const TargetIcon = iconsMap[type][state];

  return <TargetIcon width="16px" height="16px" {...props} />;
}

export default themed(FlowNodeIcon);

FlowNodeIcon.propTypes = {
  state: PropTypes.oneOf(Object.values(ACTIVITY_STATE)),
  type: PropTypes.oneOf(Object.values(ACTIVITY_TYPE)),
  theme: PropTypes.string.isRequired
};
