/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {DiagramModel} from 'bpmn-moddle';

export function isFlowNode(businessObject: BusinessObject) {
  return businessObject.$instanceOf('bpmn:FlowNode');
}

export function getFlowNodes(elementsById?: DiagramModel['elementsById']) {
  if (elementsById === undefined) {
    return [];
  }

  return Object.values(elementsById).filter((businessObject) =>
    isFlowNode(businessObject)
  );
}
