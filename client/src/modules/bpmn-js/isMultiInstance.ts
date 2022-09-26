/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {BpmnElement, BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

function isMultiInstance(element: BpmnElement | BusinessObject) {
  function isBpmnElement(
    element: BpmnElement | BusinessObject
  ): element is BpmnElement {
    return 'businessObject' in element;
  }

  return isBpmnElement(element)
    ? element.businessObject.loopCharacteristics?.$type ===
        'bpmn:MultiInstanceLoopCharacteristics'
    : element.loopCharacteristics?.$type ===
        'bpmn:MultiInstanceLoopCharacteristics';
}

export {isMultiInstance};
