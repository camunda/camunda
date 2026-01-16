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
    const businessObject = {
      id: 'adHocSubProcess',
      $type: 'bpmn:AdHocSubProcess',
    } as BusinessObject;

    expect(isMigratableFlowNode(businessObject)).toBe(true);
  });

  describe('conditional events', () => {
    it('should return true for boundary event with conditional event definition', () => {
      const businessObject = {
        id: 'boundaryEvent',
        $type: 'bpmn:BoundaryEvent',
        eventDefinitions: [
          {
            $type: 'bpmn:ConditionalEventDefinition',
          },
        ],
      } as BusinessObject;

      expect(isMigratableFlowNode(businessObject)).toBe(true);
    });

    it('should return true for intermediate catch event with conditional event definition', () => {
      const businessObject = {
        id: 'intermediateCatchEvent',
        $type: 'bpmn:IntermediateCatchEvent',
        eventDefinitions: [
          {
            $type: 'bpmn:ConditionalEventDefinition',
          },
        ],
      } as BusinessObject;

      expect(isMigratableFlowNode(businessObject)).toBe(true);
    });

    it('should return true for event subprocess with conditional start event', () => {
      const startEventBusinessObject = {
        id: 'startEvent',
        $type: 'bpmn:StartEvent',
        eventDefinitions: [
          {
            $type: 'bpmn:ConditionalEventDefinition',
          },
        ],
      } as BusinessObject;

      const businessObject = {
        id: 'eventSubProcess',
        $type: 'bpmn:SubProcess',
        triggeredByEvent: true,
        flowElements: [startEventBusinessObject],
      } as BusinessObject;

      expect(isMigratableFlowNode(businessObject)).toBe(true);
    });

    it('should return true for start event with conditional event definition inside event subprocess', () => {
      const parentBusinessObject = {
        id: 'eventSubProcess',
        $type: 'bpmn:SubProcess',
        triggeredByEvent: true,
      } as BusinessObject;

      const businessObject = {
        id: 'startEvent',
        $type: 'bpmn:StartEvent',
        $parent: parentBusinessObject,
        eventDefinitions: [
          {
            $type: 'bpmn:ConditionalEventDefinition',
          },
        ],
      } as BusinessObject;

      expect(isMigratableFlowNode(businessObject)).toBe(true);
    });
  });
});
