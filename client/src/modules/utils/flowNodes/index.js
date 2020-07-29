/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function isFlowNode(bpmnElement) {
  if (typeof bpmnElement.businessObject !== 'undefined') {
    bpmnElement = bpmnElement.businessObject;
  }

  return bpmnElement.$instanceOf('bpmn:FlowNode');
}

export function getFlowNodes(bpmnElements) {
  if (!bpmnElements) {
    return [];
  }

  let flowNodes = [];

  Object.values(bpmnElements).forEach((bpmnElement) => {
    if (isFlowNode(bpmnElement)) {
      flowNodes.push(bpmnElement);
    }
  });

  return flowNodes;
}
