/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  STATE,
  TYPE,
  FLOWNODE_TYPE_HANDLE,
  MULTI_INSTANCE_TYPE
} from 'modules/constants';
import * as api from 'modules/api/instances';
import {isFlowNode} from 'modules/utils/flowNodes/flowNodes';

function getEventType(bpmnElement) {
  // doesn't return a event type when element of type 'multiple event'
  return (
    bpmnElement.eventDefinitions &&
    bpmnElement.eventDefinitions.length === 1 &&
    FLOWNODE_TYPE_HANDLE[bpmnElement.eventDefinitions[0].$type]
  );
}

function getElementType(bpmnElement) {
  if (bpmnElement.$type === 'bpmn:BoundaryEvent') {
    return bpmnElement.cancelActivity === false
      ? TYPE.EVENT_BOUNDARY_NON_INTERURPTING
      : TYPE.EVENT_BOUNDARY_INTERRUPTING;
  } else {
    return FLOWNODE_TYPE_HANDLE[bpmnElement.$type];
  }
}

function getMultiInstanceType(bpmnElement) {
  if (!bpmnElement.loopCharacteristics) {
    return;
  }

  return bpmnElement.loopCharacteristics.isSequential
    ? MULTI_INSTANCE_TYPE.SEQUENTIAL
    : MULTI_INSTANCE_TYPE.PARALLEL;
}

/**
 * @returns {Map} activityId -> name, flowNodeType, eventType
 * @param {*} elementRegistry (bpmn elementRegistry)
 */

export function createNodeMetaDataMap(bpmnElements) {
  return Object.entries(bpmnElements).reduce(
    (map, [activityId, bpmnElement]) => {
      map.set(activityId, {
        name: bpmnElement.name,
        type: {
          elementType: getElementType(bpmnElement),
          eventType: getEventType(bpmnElement),
          multiInstanceType: getMultiInstanceType(bpmnElement)
        }
      });

      return map;
    },
    new Map()
  );
}

/**
 * Filter for selectable bpmnElements only
 * @param {Object} bpmnElements
 * @return {Object} All selectable flowNodes, excluded: sequenceFlows, diagramElements etc.
 */

export function getSelectableFlowNodes(bpmnElements) {
  if (!bpmnElements) {
    return {};
  }

  let bpmnElementSubset = {};

  Object.entries(bpmnElements).forEach(([key, bpmnElement]) => {
    if (isFlowNode(bpmnElement)) {
      bpmnElementSubset[key] = bpmnElement;
    }
  });

  return bpmnElementSubset;
}

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

export function isRunningInstance(instance) {
  return instance.state === STATE.ACTIVE || instance.state === STATE.INCIDENT;
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

export async function fetchIncidents(instance) {
  return instance.state === 'INCIDENT'
    ? await api.fetchWorkflowInstanceIncidents(instance.id)
    : null;
}

export async function fetchVariables({id}, {treeRowIds}) {
  return treeRowIds.length === 1
    ? await api.fetchVariables({instanceId: id, scopeId: treeRowIds[0]})
    : null;
}

// Subscription Handlers;

export function storeResponse({response, state}, callback) {
  if (state === 'LOADED') {
    response && callback(response);
  }
}
