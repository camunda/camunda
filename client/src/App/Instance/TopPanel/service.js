/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE, TYPE} from 'modules/constants';

export function mapify(arrayOfObjects, uniqueKey, modifier) {
  return arrayOfObjects.reduce((acc, object) => {
    const modifiedObj = modifier ? modifier(object) : object;
    return acc.set(modifiedObj[uniqueKey], modifiedObj);
  }, new Map());
}

export function getSelectedFlowNodeName(selectedFlowNodeId, nodeMetaData) {
  return !selectedFlowNodeId
    ? null
    : (nodeMetaData && nodeMetaData.name) || selectedFlowNodeId;
}

/**
 * @returns {Array} of flow node state overlays to be added the diagram.
 * @param {*} flowNodeIdToFlowNodeInstanceMap
 */
export function getFlowNodeStateOverlays(flowNodeIdToFlowNodeInstanceMap) {
  return [...flowNodeIdToFlowNodeInstanceMap.entries()].reduce(
    (overlays, [id, flowNodeInstancesMap]) => {
      const {state, type} = [...flowNodeInstancesMap.values()].reverse()[0];

      // If the activity is completed, only push an overlay
      // if the activity is an end event.
      const shouldPushOverlay =
        state !== STATE.COMPLETED ? true : type === 'END_EVENT';

      return !shouldPushOverlay ? overlays : [...overlays, {id, state}];
    },
    []
  );
}
function hasMultiInstanceActivities(instances) {
  return instances.some(
    (instance) => instance.type === TYPE.MULTI_INSTANCE_BODY
  );
}
function filterMultiInstanceActivities(flowNodeInstancesMap, filterFn) {
  const flowNodeInstances = [...flowNodeInstancesMap.values()];

  if (hasMultiInstanceActivities(flowNodeInstances)) {
    return flowNodeInstances.reduce(
      (ids, instance) => (filterFn(instance) ? [...ids, instance.id] : ids),
      []
    );
  } else {
    return [...flowNodeInstancesMap.keys()];
  }
}

/**
 * @returns {Array} an array containing activity instance ids (excluding multi instance children)
 * @param {Map} flowNodeInstancesMap
 */
export function getMultiInstanceBodies(flowNodeInstancesMap) {
  return filterMultiInstanceActivities(
    flowNodeInstancesMap,
    (instance) => instance.type === TYPE.MULTI_INSTANCE_BODY
  );
}

/**
 * @returns {Array} an array containing activity instance ids (excluding multi instance bodies)
 * @param {Map} flowNodeInstancesMap
 */
export function getMultiInstanceChildren(flowNodeInstancesMap) {
  return filterMultiInstanceActivities(
    flowNodeInstancesMap,
    (instance) => instance.type !== TYPE.MULTI_INSTANCE_BODY
  );
}
