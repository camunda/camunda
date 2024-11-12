/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BpmnElement} from 'bpmn-js/lib/NavigatedViewer';
import {isFlowNode} from 'modules/utils/flowNodes';

function isNonSelectableFlowNode(
  bpmnElement: BpmnElement,
  selectableFlowNodes?: string[],
) {
  return (
    selectableFlowNodes !== undefined &&
    !selectableFlowNodes.includes(bpmnElement.businessObject.id) &&
    bpmnElement.type !== 'label' &&
    isFlowNode(bpmnElement.businessObject)
  );
}

export {isNonSelectableFlowNode};
