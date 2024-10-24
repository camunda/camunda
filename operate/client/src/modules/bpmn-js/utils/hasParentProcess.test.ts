/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {hasParentProcess} from './hasParentProcess';

describe('hasParentProcess', () => {
  it('should return true (parent process found)', () => {
    const flowNode: BusinessObject = {
      id: 'StartEvent_1',
      name: 'Start Event 1',
      $type: 'bpmn:StartEvent',
      $parent: {
        id: 'SubProcess_1',
        name: 'Sub Process 1',
        $type: 'bpmn:SubProcess',
        $parent: {
          id: 'Process_1',
          name: 'Process 1',
          $type: 'bpmn:Process',
        },
      },
    };

    expect(hasParentProcess({flowNode, bpmnProcessId: 'Process_1'})).toBe(true);
  });

  it('should return false (parent process not found)', () => {
    const flowNode: BusinessObject = {
      id: 'StartEvent_1',
      name: 'Start Event 1',
      $type: 'bpmn:StartEvent',
      $parent: {
        id: 'SubProcess_1',
        name: 'Sub Process 1',
        $type: 'bpmn:SubProcess',
        $parent: {
          id: 'Process_1',
          name: 'Process 1',
          $type: 'bpmn:Process',
        },
      },
    };

    expect(hasParentProcess({flowNode, bpmnProcessId: 'Process_2'})).toBe(
      false,
    );
  });
});
