/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {hasEmbeddedForm} from './hasEmbeddedForm';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

describe('hasEmbeddedForm', () => {
  it('should return false for non-user tasks', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:ServiceTask',
      id: 'task1',
      name: 'Task 1',
    };

    expect(hasEmbeddedForm(businessObject)).toBe(false);
  });

  it('should return false for Zeebe user tasks without formKey', () => {
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

    expect(hasEmbeddedForm(businessObject)).toBe(false);
  });

  it('should return false for Zeebe user tasks with formKey', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:UserTask',
      id: 'task1',
      name: 'Task 1',
      extensionElements: {
        values: [
          {
            $type: 'zeebe:userTask',
          },
          {
            $type: 'zeebe:formDefinition',
            formKey: 'camunda-forms:bpmn:myForm',
          },
        ],
      },
    };

    expect(hasEmbeddedForm(businessObject)).toBe(false);
  });

  it('should return true for job-based user tasks with embedded form', () => {
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
        ],
      },
    };

    expect(hasEmbeddedForm(businessObject)).toBe(true);
  });

  it('should return false for job-based user tasks without formKey', () => {
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

    expect(hasEmbeddedForm(businessObject)).toBe(false);
  });

  it('should return false for user tasks without extension elements', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:UserTask',
      id: 'task1',
      name: 'Task 1',
    };

    expect(hasEmbeddedForm(businessObject)).toBe(false);
  });

  it('should return false for formKey that is an external form reference', () => {
    const businessObject: BusinessObject = {
      $type: 'bpmn:UserTask',
      id: 'task1',
      name: 'Task 1',
      extensionElements: {
        values: [
          {
            $type: 'zeebe:formDefinition',
            formKey: 'localhost:3000/myForm',
          },
        ],
      },
    };

    expect(hasEmbeddedForm(businessObject)).toBe(false);
  });
});
