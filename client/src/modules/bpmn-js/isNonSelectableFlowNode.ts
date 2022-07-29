/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {BpmnElement} from 'bpmn-js/lib/NavigatedViewer';
import {isFlowNode} from 'modules/utils/flowNodes';

function isNonSelectableFlowNode(
  element: BpmnElement,
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
