/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {TYPE} from 'modules/constants';

import * as Styled from './styled';
import {FlowNodeInstance} from 'modules/stores/flowNodeInstance';

const getEventFlowNode = (eventType: string, elementType: string) => {
  const map = {
    // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
    [TYPE.EVENT_TIMER]: Styled[TYPE.EVENT_TIMER + `_${elementType}`],
    // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
    [TYPE.EVENT_MESSAGE]: Styled[TYPE.EVENT_MESSAGE + `_${elementType}`],
    // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
    [TYPE.EVENT_ERROR]: Styled[TYPE.EVENT_ERROR + `_${elementType}`],
  };
  return map[eventType];
};

const flowNodes = {
  // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
  [TYPE.WORKFLOW]: Styled[TYPE.WORKFLOW],
  // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
  [TYPE.EVENT_START]: Styled[TYPE.EVENT_START],
  // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
  [TYPE.EVENT_END]: Styled[TYPE.EVENT_END],
  //Tasks
  // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
  [TYPE.TASK_SERVICE]: Styled[TYPE.TASK_SERVICE],
  // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
  [TYPE.TASK_RECEIVE]: Styled[TYPE.TASK_RECEIVE],
  // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
  [TYPE.TASK_SEND]: Styled[TYPE.TASK_SEND],
  // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
  [TYPE.TASK_SUBPROCESS]: Styled[TYPE.TASK_SUBPROCESS],
  // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
  [TYPE.TASK_CALL_ACTIVITY]: Styled[TYPE.TASK_CALL_ACTIVITY],
  //Gateways
  // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
  [TYPE.GATEWAY_EVENT_BASED]: Styled[TYPE.GATEWAY_EVENT_BASED],
  // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
  [TYPE.GATEWAY_PARALLEL]: Styled[TYPE.GATEWAY_PARALLEL],
  // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
  [TYPE.GATEWAY_EXCLUSIVE]: Styled[TYPE.GATEWAY_EXCLUSIVE],
  //Other
  // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
  [TYPE.EVENT_SUBPROCESS]: Styled[TYPE.EVENT_SUBPROCESS],
};

function getFlowNodeTypeIcon({
  elementType,
  eventType,
  multiInstanceType,
  flowNodeInstanceType,
}: {
  elementType: string;
  eventType: string;
  multiInstanceType: string;
  flowNodeInstanceType: FlowNodeInstance['type'];
}) {
  if (
    flowNodeInstanceType === TYPE.MULTI_INSTANCE_BODY &&
    multiInstanceType !== undefined
  ) {
    // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
    return Styled[multiInstanceType];
  }
  return eventType === undefined
    ? // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
      flowNodes[elementType] || Styled[TYPE.TASK_DEFAULT]
    : // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
      getEventFlowNode(eventType, elementType) || Styled[TYPE.EVENT_START];
}

type FlowNodeIconProps = {
  types: any;
  flowNodeInstanceType: FlowNodeInstance['type'];
  isSelected?: boolean;
};

function FlowNodeIcon({
  types,
  flowNodeInstanceType,
  isSelected,
  ...props
}: FlowNodeIconProps) {
  const TargetFlowNodeTypeIcon = getFlowNodeTypeIcon({
    ...types,
    flowNodeInstanceType,
  });
  return <TargetFlowNodeTypeIcon {...props} />;
}

export default FlowNodeIcon;
