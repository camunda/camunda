/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

export function isFlowNode(bpmnElement: any) {
  if (typeof bpmnElement.businessObject !== 'undefined') {
    bpmnElement = bpmnElement.businessObject;
  }

  return bpmnElement.$instanceOf('bpmn:FlowNode');
}

export function getFlowNodes(bpmnElements?: any) {
  if (bpmnElements === undefined) {
    return [];
  }

  let flowNodes: any = [];

  Object.values(bpmnElements).forEach((bpmnElement) => {
    if (isFlowNode(bpmnElement)) {
      flowNodes.push(bpmnElement);
    }
  });

  return flowNodes;
}
