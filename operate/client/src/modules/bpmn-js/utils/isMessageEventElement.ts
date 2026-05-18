/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {hasType} from './hasType';
import {hasEventType} from './hasEventType';

/**
 * Checks if an element is a message event element (receive task, message catch event,
 * or message boundary event).
 *
 * @param businessObject - The BPMN business object to check
 * @returns true if the element is a message event element
 */
const isMessageEventElement = (
  businessObject?: BusinessObject | null,
): boolean => {
  if (!businessObject) {
    return false;
  }

  if (hasType({businessObject, types: ['bpmn:ReceiveTask']})) {
    return true;
  }

  return (
    hasType({
      businessObject,
      types: ['bpmn:IntermediateCatchEvent', 'bpmn:BoundaryEvent'],
    }) && hasEventType({businessObject, types: ['bpmn:MessageEventDefinition']})
  );
};

export {isMessageEventElement};
