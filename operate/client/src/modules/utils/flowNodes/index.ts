/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObject, BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {DiagramModel} from 'bpmn-moddle';

export function isFlowNode(businessObject: BusinessObject) {
  return businessObject.$instanceOf?.('bpmn:FlowNode') ?? false;
}

export function getFlowNode({
  businessObjects,
  flowNodeId,
}: {
  businessObjects?: BusinessObjects;
  flowNodeId?: string;
}) {
  return flowNodeId ? businessObjects?.[flowNodeId] : undefined;
}

export function getFlowNodeName({
  businessObjects,
  flowNodeId,
}: {
  businessObjects?: BusinessObjects;
  flowNodeId?: string;
}) {
  return getFlowNode({businessObjects, flowNodeId})?.name ?? flowNodeId ?? '';
}

export function getFlowNodes(elementsById?: DiagramModel['elementsById']) {
  if (elementsById === undefined) {
    return [];
  }

  return Object.values(elementsById).filter((businessObject) =>
    isFlowNode(businessObject),
  );
}
