import React from 'react';
import PropTypes from 'prop-types';

import {ACTIVITY_STATE, FLOW_NODE_TYPE} from 'modules/constants';
import {themed} from 'modules/theme';

import * as Styled from './styled';

const flowNodeTypeIconsMap = {
  [FLOW_NODE_TYPE.TASK]: Styled.TaskDefault,
  [FLOW_NODE_TYPE.START_EVENT]: Styled.StartEvent,
  [FLOW_NODE_TYPE.END_EVENT]: Styled.EndEvent,
  [FLOW_NODE_TYPE.EXCLUSIVE_GATEWAY]: Styled.ExclusiveGateway,
  [FLOW_NODE_TYPE.PARALLEL_GATEWAY]: Styled.ParallelGateway
};

const stateIconsMap = {
  [ACTIVITY_STATE.COMPLETED]: {
    dark: Styled.CompletedDark,
    light: Styled.CompletedLight
  },
  [ACTIVITY_STATE.ACTIVE]: {
    dark: Styled.OkDark,
    light: Styled.OkLight
  },
  [ACTIVITY_STATE.TERMINATED]: {
    dark: Styled.CanceledDark,
    light: Styled.CanceledLight
  },
  [ACTIVITY_STATE.INCIDENT]: {
    dark: Styled.IncidentDark,
    light: Styled.IncidentLight
  }
};

function FlowNodeIcon({state, type, isSelected, ...props}) {
  // target flow node icon
  const TargetFlowNodeTypeIcon = flowNodeTypeIconsMap[type];

  // target state icon
  let TargetStateIcon = stateIconsMap[state][props.theme];

  // terminated and completed states have a special icon if the flownode is selected
  if (isSelected && state === ACTIVITY_STATE.TERMINATED) {
    TargetStateIcon = Styled.CanceledSelected;
  }

  if (isSelected && state === ACTIVITY_STATE.COMPLETED) {
    TargetStateIcon = Styled.CompletedSelected;
  }

  return (
    <Styled.IconContainer {...props}>
      <TargetFlowNodeTypeIcon />
      <TargetStateIcon />
    </Styled.IconContainer>
  );
}

FlowNodeIcon.propTypes = {
  state: PropTypes.oneOf(Object.values(ACTIVITY_STATE)).isRequired,
  type: PropTypes.oneOf(Object.values(FLOW_NODE_TYPE)).isRequired,
  theme: PropTypes.string.isRequired,
  isSelected: PropTypes.bool
};

export default themed(FlowNodeIcon);
