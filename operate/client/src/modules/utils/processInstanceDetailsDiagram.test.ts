/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  hasMultipleScopes,
  areInSameRunningScope,
  getAncestorScopeType,
} from './processInstanceDetailsDiagram';
import type {
  BusinessObject,
  BusinessObjects,
} from 'bpmn-js/lib/NavigatedViewer';

describe('hasMultipleScopes', () => {
  it('should return false if parent element is undefined', () => {
    const result = hasMultipleScopes(undefined, {});
    expect(result).toBe(false);
  });

  it('should return false if total running instances by element is undefined', () => {
    const parentElement: BusinessObject = {
      id: 'node1',
      name: 'Node 1',
      $type: 'bpmn:SequenceFlow',
    };
    const result = hasMultipleScopes(parentElement, undefined);
    expect(result).toBe(false);
  });

  it('should return false if total running instances by element does not contain the parent element id', () => {
    const parentElement: BusinessObject = {
      id: 'node1',
      name: 'Node 1',
      $type: 'bpmn:SequenceFlow',
    };
    const result = hasMultipleScopes(parentElement, {});
    expect(result).toBe(false);
  });

  it('should return false if the scope count is 0 or undefined', () => {
    const parentElement: BusinessObject = {
      id: 'node1',
      name: 'Node 1',
      $type: 'bpmn:SequenceFlow',
    };
    const totalRunningInstancesByElement = {
      node1: 0,
    };
    const result = hasMultipleScopes(
      parentElement,
      totalRunningInstancesByElement,
    );
    expect(result).toBe(false);
  });

  it('should return true if the scope count is greater than 1', () => {
    const parentElement: BusinessObject = {
      id: 'node1',
      name: 'Node 1',
      $type: 'bpmn:SequenceFlow',
    };
    const totalRunningInstancesByElement = {
      node1: 2,
    };
    const result = hasMultipleScopes(
      parentElement,
      totalRunningInstancesByElement,
    );
    expect(result).toBe(true);
  });

  it('should return false if the parent element has no parent and scope count is not greater than 1', () => {
    const parentElement: BusinessObject = {
      id: 'node1',
      name: 'Node 1',
      $type: 'bpmn:SequenceFlow',
      $parent: undefined,
    };
    const totalRunningInstancesByElement = {
      node1: 0,
    };
    const result = hasMultipleScopes(
      parentElement,
      totalRunningInstancesByElement,
    );
    expect(result).toBe(false);
  });

  it('should return true if a parent in the hierarchy has a scope count greater than 1', () => {
    const parentElement: BusinessObject = {
      id: 'node1',
      name: 'Node 1',
      $type: 'bpmn:Process',
      $parent: {
        id: 'node2',
        name: 'Node 2',
        $type: 'bpmn:SubProcess',
        $parent: {
          id: 'node3',
          name: 'Node 3',
          $type: 'bpmn:SubProcess',
          $parent: undefined,
        },
      },
    };

    const totalRunningInstancesByElement = {
      node1: 0,
      node2: 0,
      node3: 2, // Parent node3 has a scope count greater than 1
    };

    const result = hasMultipleScopes(
      parentElement,
      totalRunningInstancesByElement,
    );
    expect(result).toBe(true);
  });

  it('should return false if no parent in the hierarchy has a scope count greater than 1', () => {
    const parentElement: BusinessObject = {
      id: 'node1',
      name: 'Node 1',
      $type: 'bpmn:Process',
      $parent: {
        id: 'node2',
        name: 'Node 2',
        $type: 'bpmn:SubProcess',
        $parent: {
          id: 'node3',
          name: 'Node 3',
          $type: 'bpmn:SubProcess',
          $parent: undefined,
        },
      },
    };

    const totalRunningInstancesByElement = {
      node1: 0,
      node2: 0,
      node3: 0, // No parent has a scope count greater than 1
    };

    const result = hasMultipleScopes(
      parentElement,
      totalRunningInstancesByElement,
    );
    expect(result).toBe(false);
  });

  it('should stop checking parents if a non-SubProcess parent is encountered', () => {
    const parentElement: BusinessObject = {
      id: 'node1',
      name: 'Node 1',
      $type: 'bpmn:SequenceFlow',
      $parent: {
        id: 'node2',
        name: 'Node 2',
        $type: 'bpmn:SequenceFlow',
        $parent: {
          id: 'node3',
          name: 'Node 3',
          $type: 'bpmn:SubProcess',
          $parent: undefined,
        },
      },
    };

    const totalRunningInstancesByElement = {
      node1: 0,
      node2: 2, // This node is not a SubProcess, so it should not be checked
      node3: 0,
    };

    const result = hasMultipleScopes(
      parentElement,
      totalRunningInstancesByElement,
    );
    expect(result).toBe(false);
  });

  it('should return true if a parent AdHocSubProcess element has a scope count greater than 1', () => {
    const parentElement: BusinessObject = {
      id: 'node1',
      name: 'Node 1',
      $type: 'bpmn:Process',
      $parent: {
        id: 'node2',
        name: 'Node 2',
        $type: 'bpmn:AdHocSubProcess',
        $parent: {
          id: 'node3',
          name: 'Node 3',
          $type: 'bpmn:SubProcess',
          $parent: undefined,
        },
      },
    };

    const totalRunningInstancesByElement = {
      node1: 0,
      node2: 2,
      node3: 0,
    };

    const result = hasMultipleScopes(
      parentElement,
      totalRunningInstancesByElement,
    );
    expect(result).toBe(true);
  });
});

describe('areInSameRunningScope', () => {
  it('should return false if source element does not exist', () => {
    const businessObjects: BusinessObjects = {
      node2: {
        id: 'node2',
        name: 'Node 2',
        $type: 'bpmn:ServiceTask',
      },
    };

    const result = areInSameRunningScope(businessObjects, 'node1', 'node2', {});
    expect(result).toBe(false);
  });

  it('should return false if target element does not exist', () => {
    const businessObjects: BusinessObjects = {
      node1: {
        id: 'node1',
        name: 'Node 1',
        $type: 'bpmn:ServiceTask',
      },
    };

    const result = areInSameRunningScope(businessObjects, 'node1', 'node2', {});
    expect(result).toBe(false);
  });

  it('should return false if source element has no parent', () => {
    const businessObjects: BusinessObjects = {
      node1: {
        id: 'node1',
        name: 'Node 1',
        $type: 'bpmn:ServiceTask',
        $parent: undefined,
      },
      node2: {
        id: 'node2',
        name: 'Node 2',
        $type: 'bpmn:ServiceTask',
        $parent: {
          id: 'process',
          name: 'Process',
          $type: 'bpmn:Process',
        },
      },
    };

    const result = areInSameRunningScope(businessObjects, 'node1', 'node2', {});
    expect(result).toBe(false);
  });

  it('should return false if target element has no parent', () => {
    const businessObjects: BusinessObjects = {
      node1: {
        id: 'node1',
        name: 'Node 1',
        $type: 'bpmn:ServiceTask',
        $parent: {
          id: 'process',
          name: 'Process',
          $type: 'bpmn:Process',
        },
      },
      node2: {
        id: 'node2',
        name: 'Node 2',
        $type: 'bpmn:ServiceTask',
        $parent: undefined,
      },
    };

    const result = areInSameRunningScope(businessObjects, 'node1', 'node2', {});
    expect(result).toBe(false);
  });

  it('should return true if nodes share the same parent and it has running instances', () => {
    const parentProcess: BusinessObject = {
      id: 'process',
      name: 'Process',
      $type: 'bpmn:Process',
    };

    const businessObjects: BusinessObjects = {
      node1: {
        id: 'node1',
        name: 'Node 1',
        $type: 'bpmn:ServiceTask',
        $parent: parentProcess,
      },
      node2: {
        id: 'node2',
        name: 'Node 2',
        $type: 'bpmn:ServiceTask',
        $parent: parentProcess,
      },
      process: parentProcess,
    };

    const totalRunningInstancesByElement = {
      process: 1,
    };

    const result = areInSameRunningScope(
      businessObjects,
      'node1',
      'node2',
      totalRunningInstancesByElement,
    );
    expect(result).toBe(true);
  });

  it('should return false if nodes share the same parent but it has no running instances', () => {
    const parentProcess: BusinessObject = {
      id: 'process',
      name: 'Process',
      $type: 'bpmn:Process',
    };

    const businessObjects: BusinessObjects = {
      node1: {
        id: 'node1',
        name: 'Node 1',
        $type: 'bpmn:ServiceTask',
        $parent: parentProcess,
      },
      node2: {
        id: 'node2',
        name: 'Node 2',
        $type: 'bpmn:ServiceTask',
        $parent: parentProcess,
      },
      process: parentProcess,
    };

    const totalRunningInstancesByElement = {
      process: 0,
    };

    const result = areInSameRunningScope(
      businessObjects,
      'node1',
      'node2',
      totalRunningInstancesByElement,
    );
    expect(result).toBe(false);
  });

  it('should return false if nodes have different parents', () => {
    const process1: BusinessObject = {
      id: 'process1',
      name: 'Process 1',
      $type: 'bpmn:Process',
    };

    const process2: BusinessObject = {
      id: 'process2',
      name: 'Process 2',
      $type: 'bpmn:Process',
    };

    const businessObjects: BusinessObjects = {
      node1: {
        id: 'node1',
        name: 'Node 1',
        $type: 'bpmn:ServiceTask',
        $parent: process1,
      },
      node2: {
        id: 'node2',
        name: 'Node 2',
        $type: 'bpmn:ServiceTask',
        $parent: process2,
      },
      process1,
      process2,
    };

    const totalRunningInstancesByElement = {
      process1: 1,
      process2: 1,
    };

    const result = areInSameRunningScope(
      businessObjects,
      'node1',
      'node2',
      totalRunningInstancesByElement,
    );
    expect(result).toBe(false);
  });

  it('should return false when totalRunningInstancesByElement is undefined', () => {
    const parentProcess: BusinessObject = {
      id: 'process',
      name: 'Process',
      $type: 'bpmn:Process',
    };

    const businessObjects: BusinessObjects = {
      node1: {
        id: 'node1',
        name: 'Node 1',
        $type: 'bpmn:ServiceTask',
        $parent: parentProcess,
      },
      node2: {
        id: 'node2',
        name: 'Node 2',
        $type: 'bpmn:ServiceTask',
        $parent: parentProcess,
      },
      process: parentProcess,
    };

    const result = areInSameRunningScope(
      businessObjects,
      'node1',
      'node2',
      undefined,
    );
    expect(result).toBe(false);
  });
});

describe('getAncestorScopeType', () => {
  it('should return undefined when target does not have multiple scopes', () => {
    const parentProcess: BusinessObject = {
      id: 'process',
      name: 'Process',
      $type: 'bpmn:Process',
    };

    const targetElement: BusinessObject = {
      id: 'task-1',
      name: 'Task 1',
      $type: 'bpmn:ServiceTask',
      $parent: parentProcess,
    };

    const businessObjects: BusinessObjects = {
      'task-1': targetElement,
      'task-2': {
        id: 'task-2',
        name: 'Task 2',
        $type: 'bpmn:ServiceTask',
        $parent: parentProcess,
      },
      process: parentProcess,
    };

    const totalRunningInstancesByElement = {
      process: 1,
    };

    const result = getAncestorScopeType(
      businessObjects,
      'task-2',
      'task-1',
      totalRunningInstancesByElement,
    );
    expect(result).toBeUndefined();
  });

  it('should return "sourceParent" when target has multiple scopes and nodes are in same running scope', () => {
    const parentProcess: BusinessObject = {
      id: 'process',
      name: 'Process',
      $type: 'bpmn:Process',
    };

    const subprocess: BusinessObject = {
      id: 'subprocess',
      name: 'Subprocess',
      $type: 'bpmn:SubProcess',
      $parent: parentProcess,
    };

    const targetElement: BusinessObject = {
      id: 'task-1',
      name: 'Task 1',
      $type: 'bpmn:ServiceTask',
      $parent: subprocess,
    };

    const sourceElement: BusinessObject = {
      id: 'task-2',
      name: 'Task 2',
      $type: 'bpmn:ServiceTask',
      $parent: subprocess,
    };

    const businessObjects: BusinessObjects = {
      'task-1': targetElement,
      'task-2': sourceElement,
      subprocess,
      process: parentProcess,
    };

    const totalRunningInstancesByElement = {
      subprocess: 2,
      process: 1,
    };

    const result = getAncestorScopeType(
      businessObjects,
      'task-2',
      'task-1',
      totalRunningInstancesByElement,
    );
    expect(result).toBe('sourceParent');
  });

  it('should return "inferred" when target has multiple scopes but nodes are not in same running scope', () => {
    const parentProcess: BusinessObject = {
      id: 'process',
      name: 'Process',
      $type: 'bpmn:Process',
    };

    const subprocess1: BusinessObject = {
      id: 'subprocess1',
      name: 'Subprocess 1',
      $type: 'bpmn:SubProcess',
      $parent: parentProcess,
    };

    const subprocess2: BusinessObject = {
      id: 'subprocess2',
      name: 'Subprocess 2',
      $type: 'bpmn:SubProcess',
      $parent: parentProcess,
    };

    const targetElement: BusinessObject = {
      id: 'task-1',
      name: 'Task 1',
      $type: 'bpmn:ServiceTask',
      $parent: subprocess1,
    };

    const sourceElement: BusinessObject = {
      id: 'task-2',
      name: 'Task 2',
      $type: 'bpmn:ServiceTask',
      $parent: subprocess2,
    };

    const businessObjects: BusinessObjects = {
      'task-1': targetElement,
      'task-2': sourceElement,
      subprocess1,
      subprocess2,
      process: parentProcess,
    };

    const totalRunningInstancesByElement = {
      subprocess1: 2,
      subprocess2: 1,
      process: 1,
    };

    const result = getAncestorScopeType(
      businessObjects,
      'task-2',
      'task-1',
      totalRunningInstancesByElement,
    );
    expect(result).toBe('inferred');
  });
});
