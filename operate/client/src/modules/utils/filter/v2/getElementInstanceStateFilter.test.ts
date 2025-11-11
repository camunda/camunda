/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getElementInstanceStateFilter} from './getElementInstanceStateFilter';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';

describe('getElementInstanceStateFilter', () => {
  it('should return undefined when no element is selected', () => {
    const businessObjects: BusinessObjects = {
      task1: {
        id: 'task1',
        name: 'Task 1',
        $type: 'bpmn:ServiceTask',
      },
    };

    const result = getElementInstanceStateFilter(undefined, businessObjects);

    expect(result).toBeUndefined();
  });
  it('should return undefined when businessObjects are not provided', () => {
    const result = getElementInstanceStateFilter('task1', undefined);

    expect(result).toBeUndefined();
  });

  it('should return undefined when element is not found in businessObjects', () => {
    const businessObjects: BusinessObjects = {
      task1: {
        id: 'task1',
        name: 'Task 1',
        $type: 'bpmn:ServiceTask',
      },
    };

    const result = getElementInstanceStateFilter(
      'nonExistentElement',
      businessObjects,
    );

    expect(result).toBeUndefined();
  });

  it('should return undefined when element is a process end event', () => {
    const businessObjects: BusinessObjects = {
      endEvent1: {
        id: 'endEvent1',
        name: 'End Event',
        $type: 'bpmn:EndEvent',
        $parent: {
          id: 'process1',
          name: 'Process',
          $type: 'bpmn:Process',
        },
      },
    };

    const result = getElementInstanceStateFilter('endEvent1', businessObjects);

    expect(result).toBeUndefined();
  });

  it('should return undefined when element is a subprocess end event', () => {
    const businessObjects: BusinessObjects = {
      endEvent1: {
        id: 'endEvent1',
        name: 'Subprocess End Event',
        $type: 'bpmn:EndEvent',
        $parent: {
          id: 'subprocess1',
          name: 'Subprocess',
          $type: 'bpmn:SubProcess',
        },
      },
    };

    const result = getElementInstanceStateFilter('endEvent1', businessObjects);

    expect(result).toBeUndefined();
  });

  it('should return {$neq: "COMPLETED"} when element is NOT an end event', () => {
    const businessObjects: BusinessObjects = {
      task1: {
        id: 'task1',
        name: 'Service Task',
        $type: 'bpmn:ServiceTask',
      },
    };

    const result = getElementInstanceStateFilter('task1', businessObjects);

    expect(result).toEqual({$neq: 'COMPLETED'});
  });
});
