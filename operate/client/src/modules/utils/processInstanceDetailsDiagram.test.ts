/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {hasMultipleScopes} from './processInstanceDetailsDiagram';
import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

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
});
