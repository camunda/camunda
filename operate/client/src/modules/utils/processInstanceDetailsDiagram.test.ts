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
  it('should return false if parentFlowNode is undefined', () => {
    const result = hasMultipleScopes(undefined, {});
    expect(result).toBe(false);
  });

  it('should return false if totalRunningInstancesByFlowNode is undefined', () => {
    const parentFlowNode: BusinessObject = {
      id: 'node1',
      name: 'Node 1',
      $type: 'bpmn:SequenceFlow',
    };
    const result = hasMultipleScopes(parentFlowNode, undefined);
    expect(result).toBe(false);
  });

  it('should return false if totalRunningInstancesByFlowNode does not contain the parentFlowNode id', () => {
    const parentFlowNode: BusinessObject = {
      id: 'node1',
      name: 'Node 1',
      $type: 'bpmn:SequenceFlow',
    };
    const result = hasMultipleScopes(parentFlowNode, {});
    expect(result).toBe(false);
  });

  it('should return false if the scope count is 0 or undefined', () => {
    const parentFlowNode: BusinessObject = {
      id: 'node1',
      name: 'Node 1',
      $type: 'bpmn:SequenceFlow',
    };
    const totalRunningInstancesByFlowNode = {
      node1: 0,
    };
    const result = hasMultipleScopes(
      parentFlowNode,
      totalRunningInstancesByFlowNode,
    );
    expect(result).toBe(false);
  });

  it('should return true if the scope count is greater than 1', () => {
    const parentFlowNode: BusinessObject = {
      id: 'node1',
      name: 'Node 1',
      $type: 'bpmn:SequenceFlow',
    };
    const totalRunningInstancesByFlowNode = {
      node1: 2,
    };
    const result = hasMultipleScopes(
      parentFlowNode,
      totalRunningInstancesByFlowNode,
    );
    expect(result).toBe(true);
  });

  it('should return false if the parentFlowNode has no parent and scope count is not greater than 1', () => {
    const parentFlowNode: BusinessObject = {
      id: 'node1',
      name: 'Node 1',
      $type: 'bpmn:SequenceFlow',
      $parent: undefined,
    };
    const totalRunningInstancesByFlowNode = {
      node1: 0,
    };
    const result = hasMultipleScopes(
      parentFlowNode,
      totalRunningInstancesByFlowNode,
    );
    expect(result).toBe(false);
  });

  it('should return true if a parent in the hierarchy has a scope count greater than 1', () => {
    const parentFlowNode: BusinessObject = {
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

    const totalRunningInstancesByFlowNode = {
      node1: 0,
      node2: 0,
      node3: 2, // Parent node3 has a scope count greater than 1
    };

    const result = hasMultipleScopes(
      parentFlowNode,
      totalRunningInstancesByFlowNode,
    );
    expect(result).toBe(true);
  });

  it('should return false if no parent in the hierarchy has a scope count greater than 1', () => {
    const parentFlowNode: BusinessObject = {
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

    const totalRunningInstancesByFlowNode = {
      node1: 0,
      node2: 0,
      node3: 0, // No parent has a scope count greater than 1
    };

    const result = hasMultipleScopes(
      parentFlowNode,
      totalRunningInstancesByFlowNode,
    );
    expect(result).toBe(false);
  });

  it('should stop checking parents if a non-SubProcess parent is encountered', () => {
    const parentFlowNode: BusinessObject = {
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

    const totalRunningInstancesByFlowNode = {
      node1: 0,
      node2: 2, // This node is not a SubProcess, so it should not be checked
      node3: 0,
    };

    const result = hasMultipleScopes(
      parentFlowNode,
      totalRunningInstancesByFlowNode,
    );
    expect(result).toBe(false);
  });

  it('should return true if a parent AdHocSubProcess has a scope count greater than 1', () => {
    const parentFlowNode: BusinessObject = {
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

    const totalRunningInstancesByFlowNode = {
      node1: 0,
      node2: 2,
      node3: 0,
    };

    const result = hasMultipleScopes(
      parentFlowNode,
      totalRunningInstancesByFlowNode,
    );
    expect(result).toBe(true);
  });
});

describe('areInSameRunningScope', () => {
  it('should return false if source flow node does not exist', () => {
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

  it('should return false if target flow node does not exist', () => {
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

  it('should return false if source flow node has no parent', () => {
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

  it('should return false if target flow node has no parent', () => {
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

    const totalRunningInstancesByFlowNode = {
      process: 1,
    };

    const result = areInSameRunningScope(
      businessObjects,
      'node1',
      'node2',
      totalRunningInstancesByFlowNode,
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

    const totalRunningInstancesByFlowNode = {
      process: 0,
    };

    const result = areInSameRunningScope(
      businessObjects,
      'node1',
      'node2',
      totalRunningInstancesByFlowNode,
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

    const totalRunningInstancesByFlowNode = {
      process1: 1,
      process2: 1,
    };

    const result = areInSameRunningScope(
      businessObjects,
      'node1',
      'node2',
      totalRunningInstancesByFlowNode,
    );
    expect(result).toBe(false);
  });

  it('should return false when totalRunningInstancesByFlowNode is undefined', () => {
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

    const targetFlowNode: BusinessObject = {
      id: 'task-1',
      name: 'Task 1',
      $type: 'bpmn:ServiceTask',
      $parent: parentProcess,
    };

    const businessObjects: BusinessObjects = {
      'task-1': targetFlowNode,
      'task-2': {
        id: 'task-2',
        name: 'Task 2',
        $type: 'bpmn:ServiceTask',
        $parent: parentProcess,
      },
      process: parentProcess,
    };

    const totalRunningInstancesByFlowNode = {
      process: 1,
    };

    const result = getAncestorScopeType(
      businessObjects,
      'task-2',
      'task-1',
      totalRunningInstancesByFlowNode,
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

    const targetFlowNode: BusinessObject = {
      id: 'task-1',
      name: 'Task 1',
      $type: 'bpmn:ServiceTask',
      $parent: subprocess,
    };

    const sourceFlowNode: BusinessObject = {
      id: 'task-2',
      name: 'Task 2',
      $type: 'bpmn:ServiceTask',
      $parent: subprocess,
    };

    const businessObjects: BusinessObjects = {
      'task-1': targetFlowNode,
      'task-2': sourceFlowNode,
      subprocess,
      process: parentProcess,
    };

    const totalRunningInstancesByFlowNode = {
      subprocess: 2,
      process: 1,
    };

    const result = getAncestorScopeType(
      businessObjects,
      'task-2',
      'task-1',
      totalRunningInstancesByFlowNode,
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

    const targetFlowNode: BusinessObject = {
      id: 'task-1',
      name: 'Task 1',
      $type: 'bpmn:ServiceTask',
      $parent: subprocess1,
    };

    const sourceFlowNode: BusinessObject = {
      id: 'task-2',
      name: 'Task 2',
      $type: 'bpmn:ServiceTask',
      $parent: subprocess2,
    };

    const businessObjects: BusinessObjects = {
      'task-1': targetFlowNode,
      'task-2': sourceFlowNode,
      subprocess1,
      subprocess2,
      process: parentProcess,
    };

    const totalRunningInstancesByFlowNode = {
      subprocess1: 2,
      subprocess2: 1,
      process: 1,
    };

    const result = getAncestorScopeType(
      businessObjects,
      'task-2',
      'task-1',
      totalRunningInstancesByFlowNode,
    );
    expect(result).toBe('inferred');
  });
});
