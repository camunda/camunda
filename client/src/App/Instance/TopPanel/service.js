/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE} from 'modules/constants';

export function mapify(arrayOfObjects, uniqueKey, modifier) {
  return arrayOfObjects.reduce((acc, object) => {
    const modifiedObj = modifier ? modifier(object) : object;
    return acc.set(modifiedObj[uniqueKey], modifiedObj);
  }, new Map());
}

export function getSelectedFlowNodeName(selection, nodeMetaDataMap) {
  const selectedFlowNodeId = selection && selection.flowNodeId;

  const nodeMetaData = nodeMetaDataMap.get(selectedFlowNodeId);

  return !selectedFlowNodeId
    ? null
    : (nodeMetaData && nodeMetaData.name) || selectedFlowNodeId;
}

/**
 * @returns {Array} of flow node state overlays to be added the diagram.
 * @param {*} activityIdToActivityInstanceMap
 */
export function getFlowNodeStateOverlays(activityIdToActivityInstanceMap) {
  return [...activityIdToActivityInstanceMap.entries()].reduce(
    (overlays, [id, activityInstancesMap]) => {
      const {state, type} = [...activityInstancesMap.values()].reverse()[0];

      // If the activity is completed, only push an overlay
      // if the activity is an end event.
      const shouldPushOverlay =
        state !== STATE.COMPLETED ? true : type === 'END_EVENT';

      return !shouldPushOverlay ? overlays : [...overlays, {id, state}];
    },
    []
  );
}
