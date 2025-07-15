/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {cancelAllTokens, finishMovingToken} from './modifications';
import {modificationsStore} from 'modules/stores/modifications';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {tracking} from 'modules/tracking';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';

vi.mock('modules/stores/modifications', () => ({
  modificationsStore: {
    state: {
      sourceFlowNodeIdForMoveOperation: null,
      sourceFlowNodeInstanceKeyForMoveOperation: null,
      status: 'disabled',
    },
    addCancelModification: vi.fn(),
    addMoveModification: vi.fn(),
    setStatus: vi.fn(),
    setSourceFlowNodeIdForMoveOperation: vi.fn(),
    setSourceFlowNodeInstanceKeyForMoveOperation: vi.fn(),
  },
}));

const mockedIsMultiInstance = vi.mocked(isMultiInstance);

vi.mock('modules/bpmn-js/utils/isMultiInstance', () => ({
  isMultiInstance: vi.fn(),
}));

vi.mock('modules/tracking', () => ({
  tracking: {
    track: vi.fn(),
  },
}));

describe('cancelAllTokens', () => {
  it('should add a cancel modification with the correct parameters', () => {
    const flowNodeId = 'node1';
    const totalRunningInstancesForFlowNode = 10;
    const totalRunningInstancesVisibleForFlowNode = 5;

    cancelAllTokens(
      flowNodeId,
      totalRunningInstancesForFlowNode,
      totalRunningInstancesVisibleForFlowNode,
      {},
    );

    expect(modificationsStore.addCancelModification).toHaveBeenCalledWith({
      flowNodeId,
      affectedTokenCount: totalRunningInstancesForFlowNode,
      visibleAffectedTokenCount: totalRunningInstancesVisibleForFlowNode,
      businessObjects: {},
    });
  });
});

describe('finishMovingToken', () => {
  const businessObjects: BusinessObjects = {
    StartEvent_1: {
      $type: 'bpmn:StartEvent',
      id: 'StartEvent_1',
      name: 'Start Event',
    },
    Activity_0qtp1k6: {
      $type: 'bpmn:UserTask',
      id: 'Activity_0qtp1k6',
      name: 'User Task',
    },
    Event_0bonl61: {
      id: 'Event_0bonl61',
      name: 'End Event',
      $type: 'bpmn:EndEvent',
      $parent: {
        id: 'Process_1',
        $type: 'bpmn:Process',
        name: 'Process 1',
      },
    },
  };

  beforeEach(() => {
    modificationsStore.state.sourceFlowNodeIdForMoveOperation = 'sourceNode';
    modificationsStore.state.sourceFlowNodeInstanceKeyForMoveOperation = null;
  });

  it('should track the move-token event', () => {
    finishMovingToken(5, 3, businessObjects, 'targetNode');

    expect(tracking.track).toHaveBeenCalledWith({
      eventName: 'move-token',
    });
  });

  it('should add a move modification when targetFlowNodeId is provided', () => {
    mockedIsMultiInstance.mockReturnValue(false);

    finishMovingToken(5, 3, businessObjects, 'some-process-id', 'targetNode');

    expect(modificationsStore.addMoveModification).toHaveBeenCalledWith({
      sourceFlowNodeId: 'sourceNode',
      sourceFlowNodeInstanceKey: undefined,
      targetFlowNodeId: 'targetNode',
      affectedTokenCount: 5,
      visibleAffectedTokenCount: 3,
      newScopeCount: 5,
      businessObjects,
      bpmnProcessId: 'some-process-id',
    });
  });

  it('should set newScopeCount to 1 for multi-instance source nodes', () => {
    mockedIsMultiInstance.mockReturnValue(true);

    finishMovingToken(5, 3, businessObjects, '', 'targetNode');

    expect(modificationsStore.addMoveModification).toHaveBeenCalledWith(
      expect.objectContaining({
        newScopeCount: 1,
      }),
    );
  });

  it('should not add a move modification if targetFlowNodeId is undefined', () => {
    finishMovingToken(5, 3, businessObjects, '');

    expect(modificationsStore.addMoveModification).not.toHaveBeenCalled();
  });

  it('should reset the modification state after finishing the move', () => {
    finishMovingToken(5, 3, businessObjects, 'targetNode');

    expect(modificationsStore.setStatus).toHaveBeenCalledWith('enabled');
    expect(
      modificationsStore.setSourceFlowNodeIdForMoveOperation,
    ).toHaveBeenCalledWith(null);
    expect(
      modificationsStore.setSourceFlowNodeInstanceKeyForMoveOperation,
    ).toHaveBeenCalledWith(null);
  });
});
