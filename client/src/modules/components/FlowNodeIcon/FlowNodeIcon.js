/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import PropTypes from 'prop-types';
import {TYPE} from 'modules/constants';
import {themed} from 'modules/theme';

import * as Styled from './styled';

const getEventFlowNode = (eventType, elementType) => {
  const map = {
    [TYPE.EVENT_TIMER]: Styled[TYPE.EVENT_TIMER + `_${elementType}`],
    [TYPE.EVENT_MESSAGE]: Styled[TYPE.EVENT_MESSAGE + `_${elementType}`],
    [TYPE.EVENT_ERROR]: Styled[TYPE.EVENT_ERROR + `_${elementType}`]
  };
  return map[eventType];
};

const flowNodes = {
  [TYPE.WORKFLOW]: Styled[TYPE.WORKFLOW],
  [TYPE.EVENT_START]: Styled[TYPE.EVENT_START],
  [TYPE.EVENT_END]: Styled[TYPE.EVENT_END],
  //Tasks
  [TYPE.TASK_SERVICE]: Styled[TYPE.TASK_SERVICE],
  [TYPE.TASK_RECEIVE]: Styled[TYPE.TASK_RECEIVE],
  [TYPE.TASK_SEND]: Styled[TYPE.TASK_SEND],
  [TYPE.TASK_SUBPROCESS]: Styled[TYPE.TASK_SUBPROCESS],
  [TYPE.TASK_CALL_ACTIVITY]: Styled[TYPE.TASK_CALL_ACTIVITY],
  //Gateways
  [TYPE.GATEWAY_EVENT_BASED]: Styled[TYPE.GATEWAY_EVENT_BASED],
  [TYPE.GATEWAY_PARALLEL]: Styled[TYPE.GATEWAY_PARALLEL],
  [TYPE.GATEWAY_EXCLUSIVE]: Styled[TYPE.GATEWAY_EXCLUSIVE],
  //Other
  [TYPE.EVENT_SUBPROCESS]: Styled[TYPE.EVENT_SUBPROCESS]
};

function getFlowNodeTypeIcon({elementType, eventType, multiInstanceType}) {
  if (elementType === TYPE.MULTI_INSTANCE_BODY && multiInstanceType) {
    return Styled[multiInstanceType];
  }

  return !eventType
    ? flowNodes[elementType] || Styled[TYPE.TASK_DEFAULT]
    : getEventFlowNode(eventType, elementType) || Styled[TYPE.EVENT_START];
}

function FlowNodeIcon({types, isSelected, ...props}) {
  const TargetFlowNodeTypeIcon = getFlowNodeTypeIcon(types);
  return <TargetFlowNodeTypeIcon {...props} />;
}

FlowNodeIcon.propTypes = {
  types: PropTypes.object.isRequired,
  theme: PropTypes.string.isRequired,
  isSelected: PropTypes.bool
};

export default themed(FlowNodeIcon);
