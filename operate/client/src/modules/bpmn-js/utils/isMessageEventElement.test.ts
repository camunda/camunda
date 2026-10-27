/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isMessageEventElement} from './isMessageEventElement';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

describe('isMessageEventElement', () => {
  it('should return false for undefined', () => {
    expect(isMessageEventElement(undefined)).toBe(false);
  });

  it('should return false for null', () => {
    expect(isMessageEventElement(null)).toBe(false);
  });

  it('should return true for receive tasks', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:ReceiveTask',
      id: 'task1',
      name: 'Receive Task',
    };

    expect(isMessageEventElement(businessObject)).toBe(true);
  });

  it('should return true for intermediate catch events with message event definition', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:IntermediateCatchEvent',
      id: 'event1',
      name: 'Message Catch Event',
      eventDefinitions: [{$type: 'bpmn:MessageEventDefinition'}],
    };

    expect(isMessageEventElement(businessObject)).toBe(true);
  });

  it('should return true for boundary events with message event definition', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:BoundaryEvent',
      id: 'event1',
      name: 'Message Boundary Event',
      eventDefinitions: [{$type: 'bpmn:MessageEventDefinition'}],
    };

    expect(isMessageEventElement(businessObject)).toBe(true);
  });

  it('should return false for intermediate catch events with non-message event definition', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:IntermediateCatchEvent',
      id: 'event1',
      name: 'Timer Catch Event',
      eventDefinitions: [{$type: 'bpmn:TimerEventDefinition'}],
    };

    expect(isMessageEventElement(businessObject)).toBe(false);
  });

  it('should return false for boundary events without event definitions', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:BoundaryEvent',
      id: 'event1',
      name: 'Boundary Event',
    };

    expect(isMessageEventElement(businessObject)).toBe(false);
  });

  it('should return false for non-message element types', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:ServiceTask',
      id: 'task1',
      name: 'Service Task',
    };

    expect(isMessageEventElement(businessObject)).toBe(false);
  });
});
