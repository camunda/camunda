/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObject, EventType} from 'bpmn-js/lib/NavigatedViewer';
import {hasType} from './hasType';
import {hasEventType} from './hasEventType';

const isEventSubProcess = ({
  businessObject,
  eventTypes,
}: {
  businessObject: BusinessObject;
  eventTypes?: EventType[];
}) => {
  if (
    hasType({businessObject, types: ['bpmn:SubProcess']}) &&
    businessObject.triggeredByEvent === true
  ) {
    if (eventTypes !== undefined) {
      /**
       * if event type is provided: check for event type
       */
      return businessObject.flowElements?.some((businessObject) => {
        return (
          hasType({businessObject, types: ['bpmn:StartEvent']}) &&
          hasEventType({businessObject, types: eventTypes})
        );
      });
    }

    return true;
  }
};

export {isEventSubProcess};
