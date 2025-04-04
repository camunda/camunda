/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {DiagramModel} from 'bpmn-moddle';

export function isFlowNode(businessObject: BusinessObject) {
  return businessObject.$instanceOf?.('bpmn:FlowNode') ?? false;
}

export function getFlowNode({
  diagramModel,
  flowNodeId,
}: {
  diagramModel?: DiagramModel;
  flowNodeId?: string;
}) {
  return flowNodeId ? diagramModel?.elementsById[flowNodeId] : undefined;
}

export function getFlowNodeName({
  diagramModel,
  flowNodeId,
}: {
  diagramModel?: DiagramModel;
  flowNodeId?: string;
}) {
  return getFlowNode({diagramModel, flowNodeId})?.name ?? flowNodeId;
}

export function getFlowNodes(elementsById?: DiagramModel['elementsById']) {
  if (elementsById === undefined) {
    return [];
  }

  return Object.values(elementsById).filter((businessObject) =>
    isFlowNode(businessObject),
  );
}
