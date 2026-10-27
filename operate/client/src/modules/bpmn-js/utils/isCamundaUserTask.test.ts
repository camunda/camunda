/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isCamundaUserTask} from './isCamundaUserTask';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

describe('isCamundaUserTask', () => {
  it('should return false for undefined', () => {
    expect(isCamundaUserTask(undefined)).toBe(false);
  });

  it('should return false for null', () => {
    expect(isCamundaUserTask(null)).toBe(false);
  });

  it('should return false for non-user task types', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:ServiceTask',
      id: 'task1',
      name: 'Task 1',
    };

    expect(isCamundaUserTask(businessObject)).toBe(false);
  });

  it('should return false for user tasks without extension elements', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:UserTask',
      id: 'task1',
      name: 'Task 1',
    };

    expect(isCamundaUserTask(businessObject)).toBe(false);
  });

  it('should return false for user tasks with unrelated extension elements', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:UserTask',
      id: 'task1',
      name: 'Task 1',
      extensionElements: {
        values: [
          {
            $type: 'zeebe:taskDefinition',
            type: 'myTaskType',
          },
        ],
      },
    };

    expect(isCamundaUserTask(businessObject)).toBe(false);
  });

  it('should return true for user tasks with zeebe:userTask extension', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:UserTask',
      id: 'task1',
      name: 'Task 1',
      extensionElements: {
        values: [
          {
            $type: 'zeebe:userTask',
          },
        ],
      },
    };

    expect(isCamundaUserTask(businessObject)).toBe(true);
  });

  it('should return true when zeebe:userTask is among multiple extensions', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:UserTask',
      id: 'task1',
      name: 'Task 1',
      extensionElements: {
        values: [
          {
            $type: 'zeebe:formDefinition',
            formKey: 'camunda-forms:bpmn:myForm',
          },
          {
            $type: 'zeebe:userTask',
          },
        ],
      },
    };

    expect(isCamundaUserTask(businessObject)).toBe(true);
  });
});
