import React from 'react';
import PropTypes from 'prop-types';

import {themed} from 'modules/theme';

import * as Styled from './styled';

function getFlowNodeTypeIcon(type) {
  switch (type) {
    // workflow
    case 'WORKFLOW':
      return Styled.Workflow;
    // events
    case 'END_EVENT':
      return Styled.EndEvent;
    case 'START_EVENT':
    case 'INTERMEDIATE_CATCH_EVENT':
    case 'BOUNDARY_EVENT':
      return Styled.StartEvent;
    // gateways
    case 'EXCLUSIVE_GATEWAY':
      return Styled.ExclusiveGateway;
    case 'PARALLEL_GATEWAY':
      return Styled.ParallelGateway;
    // fallback the rest to Task
    default:
      return Styled.TaskDefault;
  }
}

function FlowNodeIcon({type, isSelected, ...props}) {
  // target flow node icon
  const TargetFlowNodeTypeIcon = getFlowNodeTypeIcon(type);

  return <TargetFlowNodeTypeIcon {...props} />;
}

FlowNodeIcon.propTypes = {
  type: PropTypes.string.isRequired,
  theme: PropTypes.string.isRequired,
  isSelected: PropTypes.bool
};

export default themed(FlowNodeIcon);
