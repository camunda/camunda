/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObject, BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';

const hasMultipleScopes = (
  parentFlowNode?: BusinessObject,
  totalRunningInstancesByFlowNode?: Record<string, number>,
): boolean => {
  if (parentFlowNode === undefined) {
    return false;
  }

  const totalRunningInstancesCount =
    totalRunningInstancesByFlowNode?.[parentFlowNode.id];

  const scopeCount = totalRunningInstancesCount ?? 0;

  if (scopeCount > 1) {
    return true;
  }

  if (parentFlowNode.$parent?.$type !== 'bpmn:SubProcess') {
    return false;
  }

  return hasMultipleScopes(
    parentFlowNode.$parent,
    totalRunningInstancesByFlowNode,
  );
};

const getFlowNodesInBetween = (
  businessObjects: BusinessObjects,
  fromFlowNodeId: string,
  toFlowNodeId: string,
): string[] => {
  const fromFlowNode = businessObjects[fromFlowNodeId];

  if (
    fromFlowNode?.$parent === undefined ||
    fromFlowNode.$parent.id === toFlowNodeId
  ) {
    return [];
  }

  return [
    fromFlowNode.$parent.id,
    ...getFlowNodesInBetween(
      businessObjects,
      fromFlowNode.$parent.id,
      toFlowNodeId,
    ),
  ];
};

const getFlowNodeParents = (
  businessObjects: BusinessObjects,
  flowNodeId: string,
): string[] => {
  const bpmnProcessId =
    processInstanceDetailsStore.state.processInstance?.bpmnProcessId;

  if (bpmnProcessId === undefined) {
    return [];
  }

  return getFlowNodesInBetween(businessObjects, flowNodeId, bpmnProcessId);
};

export {getFlowNodeParents, hasMultipleScopes};
