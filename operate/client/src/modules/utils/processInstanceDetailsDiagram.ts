/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  BusinessObject,
  BusinessObjects,
} from 'bpmn-js/lib/NavigatedViewer';

const checkScope = (
  parentFlowNode: BusinessObject | undefined,
  totalRunningInstancesByFlowNode: Record<string, number> | undefined,
  predicate: (count: number) => boolean,
): boolean => {
  if (!parentFlowNode) {
    return false;
  }

  const count = totalRunningInstancesByFlowNode?.[parentFlowNode.id] ?? 0;

  // If predicate matches, return immediately
  if (predicate(count)) {
    return true;
  }

  // If not inside a SubProcess, stop recursion
  if (parentFlowNode.$parent?.$type !== 'bpmn:SubProcess') {
    return false;
  }

  // Recurse upwards
  return checkScope(
    parentFlowNode.$parent,
    totalRunningInstancesByFlowNode,
    predicate,
  );
};

const hasMultipleScopes = (
  parentFlowNode?: BusinessObject,
  totalRunningInstancesByFlowNode?: Record<string, number>,
): boolean =>
  checkScope(
    parentFlowNode,
    totalRunningInstancesByFlowNode,
    (count) => count > 1,
  );

const hasSingleScope = (
  parentFlowNode?: BusinessObject,
  totalRunningInstancesByFlowNode?: Record<string, number>,
): boolean =>
  checkScope(
    parentFlowNode,
    totalRunningInstancesByFlowNode,
    (count) => count === 1,
  );

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
  bpmnProcessId?: string,
): string[] => {
  if (bpmnProcessId === undefined) {
    return [];
  }

  return getFlowNodesInBetween(businessObjects, flowNodeId, bpmnProcessId);
};

export {
  getFlowNodeParents,
  hasMultipleScopes,
  hasSingleScope,
  getFlowNodesInBetween,
};
