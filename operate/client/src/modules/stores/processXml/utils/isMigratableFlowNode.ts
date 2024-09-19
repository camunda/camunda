/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {hasEventType} from 'modules/bpmn-js/utils/hasEventType';
import {hasType} from 'modules/bpmn-js/utils/hasType';
import {isEventSubProcess} from 'modules/bpmn-js/utils/isEventSubProcess';

const isMigratableFlowNode = (businessObject: BusinessObject) => {
  /**
   * Check boundary events
   */
  if (
    hasType({
      businessObject,
      types: ['bpmn:BoundaryEvent'],
    }) &&
    hasEventType({
      businessObject,
      types: ['bpmn:MessageEventDefinition', 'bpmn:TimerEventDefinition'],
    })
  ) {
    return true;
  }

  /**
   * Check intermediate catch events
   */
  if (
    hasType({
      businessObject,
      types: ['bpmn:IntermediateCatchEvent'],
    }) &&
    hasEventType({
      businessObject,
      types: ['bpmn:MessageEventDefinition', 'bpmn:TimerEventDefinition'],
    })
  ) {
    return true;
  }

  /**
   * Check event sub processes
   */
  if (
    isEventSubProcess({
      businessObject,
    })
  ) {
    if (
      isEventSubProcess({
        businessObject,
        eventTypes: [
          'bpmn:MessageEventDefinition',
          'bpmn:TimerEventDefinition',
        ],
      })
    ) {
      return true; // message and timer event sub processes
    } else {
      return false; // any other event sub process
    }
  }

  return hasType({
    businessObject,
    types: [
      'bpmn:ServiceTask',
      'bpmn:UserTask',
      'bpmn:SubProcess',
      'bpmn:CallActivity',
      'bpmn:ReceiveTask',
    ],
  });
};

export {isMigratableFlowNode};
