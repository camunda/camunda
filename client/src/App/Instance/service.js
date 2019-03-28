/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE} from 'modules/constants';
import {fetchActivityInstancesTree} from 'modules/api/activityInstances';
import * as api from 'modules/api/instances';

/**
 * @returns activityId -> name map
 * @param {*} elementRegistry (bpmn elementRegistry)
 */
export function getActivityIdToNameMap(elements) {
  return Object.entries(elements).reduce((map, [id, element]) => {
    if (element.name) {
      map.set(id, element.name);
    }

    return map;
  }, new Map());
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

export function isRunningInstance(state) {
  return state === STATE.ACTIVE || state === STATE.INCIDENT;
}

/**
 * transforms the activities instrances tree to
 * @return activityIdToActivityInstanceMap: (activityId -> activityInstance) map
 * @param {*} activitiesInstancesTree
 * @param {*} [activityIdToActivityInstanceMap] optional
 */
export function getActivityIdToActivityInstancesMap(
  activitiesInstancesTree,
  activityIdToActivityInstanceMap = new Map()
) {
  const {children} = activitiesInstancesTree;

  return children.reduce(
    (activityIdToActivityInstanceMap, activityInstance) => {
      const {id, activityId} = activityInstance;

      // update activityIdToActivityInstanceMap
      const activityInstancesMap =
        activityIdToActivityInstanceMap.get(activityId) || new Map();

      activityInstancesMap.set(id, activityInstance);

      activityIdToActivityInstanceMap.set(activityId, activityInstancesMap);

      return !activityInstance.children
        ? activityIdToActivityInstanceMap
        : getActivityIdToActivityInstancesMap(
            activityInstance,
            activityIdToActivityInstanceMap
          );
    },
    activityIdToActivityInstanceMap
  );
}

export async function fetchActivityInstancesTreeData(instance) {
  const activitiesInstancesTree = await fetchActivityInstancesTree(instance.id);

  return {
    activityInstancesTree: {
      ...activitiesInstancesTree,
      id: instance.id,
      type: 'WORKFLOW',
      state: instance.state,
      endDate: instance.endDate
    },
    activityIdToActivityInstanceMap: getActivityIdToActivityInstancesMap(
      activitiesInstancesTree
    )
  };
}

export async function fetchIncidents(instance) {
  return instance.state === 'INCIDENT'
    ? await api.fetchWorkflowInstanceIncidents(instance.id)
    : null;
}

export async function fetchVariables({id}, {treeRowIds}) {
  return treeRowIds.length === 1
    ? await api.fetchVariables(id, treeRowIds[0])
    : null;
}
