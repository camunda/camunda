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

const getElementsInBetween = (
  businessObjects: BusinessObjects,
  fromElementId: string,
  toElementId: string,
): string[] => {
  const fromElement = businessObjects[fromElementId];

  if (
    fromElement?.$parent === undefined ||
    fromElement.$parent.id === toElementId
  ) {
    return [];
  }

  return [
    fromElement.$parent.id,
    ...getElementsInBetween(
      businessObjects,
      fromElement.$parent.id,
      toElementId,
    ),
  ];
};

const getElementParents = (
  businessObjects: BusinessObjects,
  elementId: string,
  bpmnProcessId?: string,
): string[] => {
  if (bpmnProcessId === undefined) {
    return [];
  }

  return getElementsInBetween(businessObjects, elementId, bpmnProcessId);
};

const areInSameRunningScope = (
  businessObjects: BusinessObjects,
  sourceElementId: string,
  targetElementId: string,
  totalRunningInstancesByFlowNode?: Record<string, number>,
): boolean => {
  const sourceElement = businessObjects[sourceElementId];
  const targetElement = businessObjects[targetElementId];

  if (!sourceElement || !targetElement) {
    return false;
  }

  const sourceParent = sourceElement.$parent;
  const targetParent = targetElement.$parent;

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
  getElementParents,
  hasMultipleScopes,
  hasSingleScope,
  getElementsInBetween,
  areInSameRunningScope,
  getAncestorScopeType,
};
