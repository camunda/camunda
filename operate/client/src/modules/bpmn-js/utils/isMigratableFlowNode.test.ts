/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {isMigratableFlowNode} from './isMigratableFlowNode';

describe('isMigratableFlowNode', () => {
  it('should return true for ad hoc sub process', () => {
    const businessObject: BusinessObject = {
      id: 'adHocSubProcess',
      name: 'Ad Hoc Sub Process',
      $type: 'bpmn:AdHocSubProcess',
    };

    expect(isMigratableFlowNode(businessObject)).toBe(true);
  });

  it('should return true for boundary event with conditional event definition', () => {
    const businessObject: BusinessObject = {
      id: 'boundaryEvent',
      name: 'Boundary Event',
      $type: 'bpmn:BoundaryEvent',
      eventDefinitions: [
        {
          $type: 'bpmn:ConditionalEventDefinition',
        },
      ],
    };

    expect(isMigratableFlowNode(businessObject)).toBe(true);
  });

  it('should return true for intermediate catch event with conditional event definition', () => {
    const businessObject: BusinessObject = {
      id: 'intermediateCatchEvent',
      name: 'Intermediate Catch Event',
      $type: 'bpmn:IntermediateCatchEvent',
      eventDefinitions: [
        {
          $type: 'bpmn:ConditionalEventDefinition',
        },
      ],
    };

    expect(isMigratableFlowNode(businessObject)).toBe(true);
  });

  it('should return true for event subprocess with conditional start event', () => {
    const startEventBusinessObject: BusinessObject = {
      id: 'startEvent',
      name: 'Start Event',
      $type: 'bpmn:StartEvent',
      eventDefinitions: [
        {
          $type: 'bpmn:ConditionalEventDefinition',
        },
      ],
    };

    const businessObject: BusinessObject = {
      id: 'eventSubProcess',
      name: 'Event Sub Process',
      $type: 'bpmn:SubProcess',
      triggeredByEvent: true,
      flowElements: [startEventBusinessObject],
    };

    expect(isMigratableFlowNode(businessObject)).toBe(true);
  });

  it('should return true for start event with conditional event definition inside event subprocess', () => {
    const parentBusinessObject: BusinessObject = {
      id: 'eventSubProcess',
      name: 'Event Sub Process',
      $type: 'bpmn:SubProcess',
      triggeredByEvent: true,
    };

    const businessObject: BusinessObject = {
      id: 'startEvent',
      name: 'Start Event',
      $type: 'bpmn:StartEvent',
      $parent: parentBusinessObject,
      eventDefinitions: [
        {
          $type: 'bpmn:ConditionalEventDefinition',
        },
      ],
    };

    expect(isMigratableFlowNode(businessObject)).toBe(true);
  });
});
