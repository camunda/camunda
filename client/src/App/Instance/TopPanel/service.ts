/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE, TYPE} from 'modules/constants';
import {compactObject} from 'modules/utils';
import {formatDate} from 'modules/utils/date';

export function getSelectedFlowNodeName(
  selectedFlowNodeId: any,
  nodeMetaData: any
) {
  return !selectedFlowNodeId
    ? null
    : (nodeMetaData && nodeMetaData.name) || selectedFlowNodeId;
}

/**
 * @returns {Array} of flow node state overlays to be added the diagram.
 * @param {*} flowNodeIdToFlowNodeInstanceMap
 */
export function getFlowNodeStateOverlays(flowNodeIdToFlowNodeInstanceMap: any) {
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
function hasMultiInstanceActivities(instances: any) {
  return instances.some(
    (instance: any) => instance.type === TYPE.MULTI_INSTANCE_BODY
  );
}
function filterMultiInstanceActivities(
  flowNodeInstancesMap: any,
  filterFn: any
) {
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
export function getMultiInstanceBodies(flowNodeInstancesMap: any) {
  return filterMultiInstanceActivities(
    flowNodeInstancesMap,
    (instance: any) => instance.type === TYPE.MULTI_INSTANCE_BODY
  );
}

/**
 * @returns {Array} an array containing activity instance ids (excluding multi instance bodies)
 * @param {Map} flowNodeInstancesMap
 */
export function getMultiInstanceChildren(flowNodeInstancesMap: any) {
  return filterMultiInstanceActivities(
    flowNodeInstancesMap,
    (instance: any) => instance.type !== TYPE.MULTI_INSTANCE_BODY
  );
}

export function getCurrentMetadata(
  events: any,
  flowNodeId: any,
  treeRowIds: any,
  flowNodeIdToFlowNodeInstanceMap: any,
  areMultipleNodesSelected: any
) {
  if (areMultipleNodesSelected) {
    return {
      isMultiRowPeterCase: true,
      instancesCount: treeRowIds.length,
    };
  }

  const flowNodeInstancesMap = flowNodeIdToFlowNodeInstanceMap.get(flowNodeId);

  // get the last event corresponding to the given flowNodeId (= activityId)
  const {activityInstanceId, metadata} = events.reduce(
    (acc: any, event: any) => {
      return event.activityInstanceId === treeRowIds[0] ? event : acc;
    },
    null
  );

  // get corresponding start and end dates
  const activityInstance = flowNodeInstancesMap.get(activityInstanceId);

  const startDate = formatDate(activityInstance.startDate);
  const endDate = formatDate(activityInstance.endDate);

  const isMultiInstanceBody =
    activityInstance.type === TYPE.MULTI_INSTANCE_BODY;

  const parentActivity = flowNodeInstancesMap.get(activityInstance.parentId);
  const isMultiInstanceChild =
    parentActivity && parentActivity.type === TYPE.MULTI_INSTANCE_BODY;

  // return a cleaned-up  metadata object
  return {
    ...compactObject({
      isMultiInstanceBody,
      isMultiInstanceChild,
      parentId: activityInstance.parentId,
      isSingleRowPeterCase: flowNodeInstancesMap.size > 1 ? true : null,
    }),
    data: Object.entries({
      activityInstanceId,
      ...metadata,
      startDate,
      endDate,
    }).reduce((cleanMetadata, [key, value]) => {
      // ignore other empty values
      if (!value) {
        return cleanMetadata;
      }

      return {...cleanMetadata, [key]: value};
    }, {}),
  };
}
