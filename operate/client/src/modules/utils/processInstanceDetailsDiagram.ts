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
import {isAdHocSubProcess} from 'modules/bpmn-js/utils/isAdHocSubProcess';
import {isSubProcess} from 'modules/bpmn-js/utils/isSubProcess';
import type {AncestorScopeType} from 'modules/stores/modifications';

const checkScope = (
  parentFlowNode: BusinessObject | undefined,
  totalRunningInstancesByFlowNode: Record<string, number> | undefined,
  predicate: (count: number) => boolean,
): boolean => {
  if (!parentFlowNode) {
    return false;
  }

  const count = totalRunningInstancesByFlowNode?.[parentFlowNode.id] ?? 0;

  if (predicate(count)) {
    return true;
  }

  if (
    !isSubProcess(parentFlowNode.$parent) &&
    !isAdHocSubProcess(parentFlowNode.$parent)
  ) {
    return false;
  }

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

const areInSameRunningScope = (
  businessObjects: BusinessObjects,
  sourceFlowNodeId: string,
  targetFlowNodeId: string,
  totalRunningInstancesByFlowNode?: Record<string, number>,
): boolean => {
  const sourceFlowNode = businessObjects[sourceFlowNodeId];
  const targetFlowNode = businessObjects[targetFlowNodeId];

  if (!sourceFlowNode || !targetFlowNode) {
    return false;
  }

  const sourceParent = sourceFlowNode.$parent;
  const targetParent = targetFlowNode.$parent;

  if (!sourceParent || !targetParent) {
    return false;
  }

  if (sourceParent.id === targetParent.id) {
    const runningCount =
      totalRunningInstancesByFlowNode?.[sourceParent.id] ?? 0;
    return runningCount > 0;
  }

  return false;
};

const getAncestorScopeType = (
  businessObjects: BusinessObjects,
  sourceFlowNodeId: string,
  targetFlowNodeId: string,
  totalRunningInstancesByFlowNode?: Record<string, number>,
): AncestorScopeType => {
  const targetFlowNode = businessObjects[targetFlowNodeId];

  if (!hasMultipleScopes(targetFlowNode, totalRunningInstancesByFlowNode)) {
    return;
  }

  const inSameScope = areInSameRunningScope(
    businessObjects,
    sourceFlowNodeId,
    targetFlowNodeId,
    totalRunningInstancesByFlowNode,
  );

  return inSameScope ? 'sourceParent' : 'inferred';
};

export {
  getFlowNodeParents,
  hasMultipleScopes,
  hasSingleScope,
  getFlowNodesInBetween,
  areInSameRunningScope,
  getAncestorScopeType,
};

export type {AncestorScopeType};
