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
  parentElement: BusinessObject | undefined,
  totalRunningInstancesByElement: Record<string, number> | undefined,
  predicate: (count: number) => boolean,
): boolean => {
  if (!parentElement) {
    return false;
  }

  const count = totalRunningInstancesByElement?.[parentElement.id] ?? 0;

  if (predicate(count)) {
    return true;
  }

  if (
    !isSubProcess(parentElement.$parent) &&
    !isAdHocSubProcess(parentElement.$parent)
  ) {
    return false;
  }

  return checkScope(
    parentElement.$parent,
    totalRunningInstancesByElement,
    predicate,
  );
};

const hasMultipleScopes = (
  parentElement?: BusinessObject,
  totalRunningInstancesByElement?: Record<string, number>,
): boolean =>
  checkScope(
    parentElement,
    totalRunningInstancesByElement,
    (count) => count > 1,
  );

const hasSingleScope = (
  parentElement?: BusinessObject,
  totalRunningInstancesByElement?: Record<string, number>,
): boolean =>
  checkScope(
    parentElement,
    totalRunningInstancesByElement,
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
  totalRunningInstancesByElement?: Record<string, number>,
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
    const runningCount = totalRunningInstancesByElement?.[sourceParent.id] ?? 0;
    return runningCount > 0;
  }

  return false;
};

const getAncestorScopeType = (
  businessObjects: BusinessObjects,
  sourceElementId: string,
  targetElementId: string,
  totalRunningInstancesByElement?: Record<string, number>,
): AncestorScopeType => {
  const targetElement = businessObjects[targetElementId];

  if (!hasMultipleScopes(targetElement, totalRunningInstancesByElement)) {
    return;
  }

  const inSameScope = areInSameRunningScope(
    businessObjects,
    sourceElementId,
    targetElementId,
    totalRunningInstancesByElement,
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
