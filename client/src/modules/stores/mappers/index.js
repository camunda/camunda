/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

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

export {constructFlowNodeIdToFlowNodeInstanceMap};
