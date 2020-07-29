/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isFlowNode} from 'modules/utils/flowNodes';
import {
  TYPE,
  FLOWNODE_TYPE_HANDLE,
  MULTI_INSTANCE_TYPE,
} from 'modules/constants';
/**
 * transforms the activities instances tree to
 * @return activityIdToActivityInstanceMap: (activityId -> activityInstance) map
 * @param {*} activitiesInstancesTree
 * @param {*} [activityIdToActivityInstanceMap] optional
 */
const constructFlowNodeIdToFlowNodeInstanceMap = function (
  flowNodesInstancesTree,
  flowNodeIdToFlowNodeInstanceMap = new Map()
) {
  const {children} = flowNodesInstancesTree;

  return children.reduce(
    (flowNodeIdToFlowNodeInstanceMap, flowNodeInstance) => {
      const {id, activityId: flowNodeId} = flowNodeInstance;

      // update flowNodeIdToFlowNodeInstanceMap
      const flowNodeInstancesMap =
        flowNodeIdToFlowNodeInstanceMap.get(flowNodeId) || new Map();

      flowNodeInstancesMap.set(id, flowNodeInstance);

      flowNodeIdToFlowNodeInstanceMap.set(flowNodeId, flowNodeInstancesMap);

      return !flowNodeInstance.children
        ? flowNodeIdToFlowNodeInstanceMap
        : constructFlowNodeIdToFlowNodeInstanceMap(
            flowNodeInstance,
            flowNodeIdToFlowNodeInstanceMap
          );
    },
    flowNodeIdToFlowNodeInstanceMap
  );
};

const getSelectableFlowNodes = (bpmnElements) => {
  if (!bpmnElements) {
    return {};
  }

  return Object.entries(bpmnElements).reduce(
    (accumulator, [key, bpmnElement]) => {
      if (isFlowNode(bpmnElement)) {
        return {...accumulator, [key]: bpmnElement};
      }
      return accumulator;
    },
    {}
  );
};

const createNodeMetaDataMap = (bpmnElements) => {
  return Object.entries(bpmnElements).reduce(
    (map, [activityId, bpmnElement]) => {
      map.set(activityId, {
        name: bpmnElement.name,
        type: {
          elementType: getElementType(bpmnElement),
          eventType: getEventType(bpmnElement),
          multiInstanceType: getMultiInstanceType(bpmnElement),
        },
      });

      return map;
    },
    new Map()
  );
};

const getEventType = (bpmnElement) => {
  // doesn't return a event type when element of type 'multiple event'
  return (
    bpmnElement.eventDefinitions?.length === 1 &&
    FLOWNODE_TYPE_HANDLE[bpmnElement.eventDefinitions[0].$type]
  );
};

const getElementType = (bpmnElement) => {
  const {$type: type, cancelActivity, triggeredByEvent} = bpmnElement;

  if (type === 'bpmn:SubProcess' && triggeredByEvent) {
    return TYPE.EVENT_SUBPROCESS;
  }
  if (type === 'bpmn:BoundaryEvent') {
    return cancelActivity === false
      ? TYPE.EVENT_BOUNDARY_NON_INTERRUPTING
      : TYPE.EVENT_BOUNDARY_INTERRUPTING;
  } else {
    return FLOWNODE_TYPE_HANDLE[type];
  }
};

const getMultiInstanceType = (bpmnElement) => {
  if (!bpmnElement.loopCharacteristics) {
    return;
  }

  return bpmnElement.loopCharacteristics.isSequential
    ? MULTI_INSTANCE_TYPE.SEQUENTIAL
    : MULTI_INSTANCE_TYPE.PARALLEL;
};

export {
  constructFlowNodeIdToFlowNodeInstanceMap,
  getSelectableFlowNodes,
  createNodeMetaDataMap,
};
