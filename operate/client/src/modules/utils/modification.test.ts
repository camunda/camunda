/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {cancelAllTokens, finishMovingToken} from './modifications';
import {modificationsStore} from 'modules/stores/modifications';
import {tracking} from 'modules/tracking';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';

describe('cancelAllTokens', () => {
  it('should add a cancel modification with the correct parameters', () => {
    const elementId = 'node1';
    const totalRunningInstancesForElement = 10;
    const totalRunningInstancesVisibleForElement = 5;

    cancelAllTokens(
      elementId,
      totalRunningInstancesForElement,
      totalRunningInstancesVisibleForElement,
      {},
    );

    expect(modificationsStore.elementModifications).toEqual([
      expect.objectContaining({
        operation: 'CANCEL_TOKEN',
        element: {id: 'node1', name: 'node1'},
        affectedTokenCount: 10,
        visibleAffectedTokenCount: 5,
      }),
    ]);
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
    sourceNode: {
      $type: 'bpmn:ServiceTask',
      id: 'sourceNode',
      name: 'Source Node',
    },
  };

  beforeEach(() => {
    modificationsStore.setSourceElementIdForMoveOperation('sourceNode');
  });

  it('should track the move-token event', () => {
    const trackSpy = vi.spyOn(tracking, 'track');

    finishMovingToken(5, 3, businessObjects, 'targetNode');

    expect(trackSpy).toHaveBeenCalledWith({
      eventName: 'move-token',
    });
  });

  it('should add a move modification when targetElementId is provided', () => {
    finishMovingToken(5, 3, businessObjects, 'some-process-id', 'targetNode');

    const moveModifications = modificationsStore.elementModifications.filter(
      (m) => m.operation === 'MOVE_TOKEN',
    );

    expect(moveModifications).toHaveLength(1);
    expect(moveModifications[0]).toEqual(
      expect.objectContaining({
        operation: 'MOVE_TOKEN',
        element: {id: 'sourceNode', name: 'Source Node'},
        targetElement: {id: 'targetNode', name: 'targetNode'},
        affectedTokenCount: 5,
        visibleAffectedTokenCount: 3,
      }),
    );
    expect(moveModifications[0]!.scopeIds).toHaveLength(5);
  });

  it('should set newScopeCount to 1 for multi-instance source nodes', () => {
    const multiInstanceBusinessObjects: BusinessObjects = {
      ...businessObjects,
      sourceNode: {
        ...businessObjects['sourceNode']!,
        loopCharacteristics: {
          $type: 'bpmn:MultiInstanceLoopCharacteristics',
          isSequential: false,
        },
      },
    };

    finishMovingToken(5, 3, multiInstanceBusinessObjects, '', 'targetNode');

    const moveModifications = modificationsStore.elementModifications.filter(
      (m) => m.operation === 'MOVE_TOKEN',
    );

    expect(moveModifications).toHaveLength(1);
    expect(moveModifications[0]!.scopeIds).toHaveLength(1);
  });

  it('should not add a move modification if targetElementId is undefined', () => {
    finishMovingToken(5, 3, businessObjects, '');

    const moveModifications = modificationsStore.elementModifications.filter(
      (m) => m.operation === 'MOVE_TOKEN',
    );

    expect(moveModifications).toHaveLength(0);
  });

  it('should reset the modification state after finishing the move', () => {
    finishMovingToken(5, 3, businessObjects, 'targetNode');

    expect(modificationsStore.state.status).toBe('enabled');
    expect(modificationsStore.state.sourceElementIdForMoveOperation).toBeNull();
    expect(
      modificationsStore.state.sourceElementInstanceKeyForMoveOperation,
    ).toBeNull();
  });
});
