/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

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

export {hasMultipleScopes};
