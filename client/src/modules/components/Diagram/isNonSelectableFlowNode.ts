/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isFlowNode} from 'modules/utils/flowNodes';
import {BpmnJSElement} from '.';

function isNonSelectableFlowNode(
  element: BpmnJSElement,
  selectableFlowNodes?: string[]
) {
  return (
    selectableFlowNodes !== undefined &&
    !selectableFlowNodes.includes(element.id) &&
    element.type !== 'label' &&
    isFlowNode(element)
  );
}

export {isNonSelectableFlowNode};
