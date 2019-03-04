/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';

import {themed} from 'modules/theme';

import * as Styled from './styled';

function getFlowNodeTypeIcon(type) {
  switch (type) {
    // workflow
    case 'WORKFLOW':
      return Styled.Workflow;
    // tasks
    case 'SERVICE_TASK':
      return Styled.TaskService;
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
