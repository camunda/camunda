/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  getSelectedFlowNodeName,
  getSelectedRunningInstanceCount,
  selectAdHocSubProcessInnerInstance,
} from './flowNodeSelection';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {createInstance} from 'modules/testUtils';
import {vi} from 'vitest';

describe('getSelectedRunningInstanceCount', () => {
  beforeEach(() => {
    flowNodeSelectionStore.reset();
    flowNodeMetaDataStore.reset();
  });

  afterEach(() => {
    processInstanceDetailsStore.reset();
  });

  it('should return 0 if no selection is made', () => {
    flowNodeSelectionStore.setSelection(null);
    const result = getSelectedRunningInstanceCount({
      totalRunningInstancesForFlowNode: 10,
      isRootNodeSelected: false,
    });
    expect(result).toBe(0);
  });

  it('should return 0 if the selection is a placeholder', () => {
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'someNode',
      flowNodeInstanceId: 'someInstance',
      isPlaceholder: true,
    });
    const result = getSelectedRunningInstanceCount({
      totalRunningInstancesForFlowNode: 10,
      isRootNodeSelected: false,
    });
    expect(result).toBe(0);
  });

  it('should return 0 if the root node is selected', () => {
    const result = getSelectedRunningInstanceCount({
      totalRunningInstancesForFlowNode: 10,
      isRootNodeSelected: true,
    });
    expect(result).toBe(0);
  });

  it('should return 0 if the selection has no flowNodeId', () => {
    flowNodeSelectionStore.setSelection({
      flowNodeId: undefined,
    });
    const result = getSelectedRunningInstanceCount({
      totalRunningInstancesForFlowNode: 10,
      isRootNodeSelected: false,
    });
    expect(result).toBe(0);
  });

  it('should return 0 if a specific flow node instance is selected and it is not running', () => {
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'someNode',
      flowNodeInstanceId: 'someInstance',
    });
    const result = getSelectedRunningInstanceCount({
      totalRunningInstancesForFlowNode: 10,
      isRootNodeSelected: false,
    });
    expect(result).toBe(0);
  });

  it('should handle edge cases with invalid selection', () => {
    flowNodeSelectionStore.setSelection({});
    const result = getSelectedRunningInstanceCount({
      totalRunningInstancesForFlowNode: 10,
      isRootNodeSelected: false,
    });
    expect(result).toBe(0);
  });

  it('should retrieve selected flow node name from business object', () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: 'instance_id',
        state: 'ACTIVE',
      }),
    );

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'startEvent',
      flowNodeInstanceId: '2251799813689409',
    });

    const result = getSelectedFlowNodeName({
      businessObjects: {
        startEvent: {
          id: 'startEvent',
          name: 'Start Event',
          $type: 'bpmn:StartEvent',
        },
      },
      processDefinitionName: 'someProcessName',
    });

    expect(result).toBe('Start Event');
  });
});

describe('selectAdHocSubProcessInnerInstance', () => {
  beforeEach(() => {
    flowNodeSelectionStore.reset();
    flowNodeInstanceStore.reset();
  });

  const createMockFlowNodeInstance = (overrides = {}) => ({
    id: 'mock-id',
    type: 'SERVICE_TASK' as const,
    state: 'ACTIVE' as const,
    flowNodeId: 'mock-flow-node',
    startDate: '2020-08-18T12:07:33.953+0000',
    endDate: null,
    treePath: 'mock/path',
    sortValues: ['1606300828415', 'mock-id'] as [string, string],
    ...overrides,
  });

  it('should select the first child when children are already loaded', () => {
    const mockRootNode = {flowNodeId: 'root', flowNodeInstanceId: 'root-1'};
    const mockInnerInstance = createMockFlowNodeInstance({
      id: 'inner-1',
      flowNodeId: 'adhoc-subprocess',
      type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    });

    const mockChild = createMockFlowNodeInstance({
      id: 'child-1',
      flowNodeId: 'task-1',
      type: 'SERVICE_TASK',
    });

    // Mock that children are already loaded
    const getVisibleChildNodesSpy = vi
      .spyOn(flowNodeInstanceStore, 'getVisibleChildNodes')
      .mockReturnValue([mockChild]);

    selectAdHocSubProcessInnerInstance(mockRootNode, mockInnerInstance);

    expect(getVisibleChildNodesSpy).toHaveBeenCalledWith(mockInnerInstance);
    expect(flowNodeSelectionStore.state.selection).toEqual({
      flowNodeId: 'task-1',
      flowNodeInstanceId: 'child-1',
    });

    getVisibleChildNodesSpy.mockRestore();
  });

  it('should handle case when there are no children available', () => {
    const mockRootNode = {flowNodeId: 'root', flowNodeInstanceId: 'root-1'};
    const mockInnerInstance = createMockFlowNodeInstance({
      id: 'inner-1',
      flowNodeId: 'adhoc-subprocess',
      type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    });

    // Mock that no children are loaded initially
    const getVisibleChildNodesSpy = vi
      .spyOn(flowNodeInstanceStore, 'getVisibleChildNodes')
      .mockReturnValue([]);

    selectAdHocSubProcessInnerInstance(mockRootNode, mockInnerInstance);

    expect(getVisibleChildNodesSpy).toHaveBeenCalledWith(mockInnerInstance);
    expect(flowNodeSelectionStore.state.selection).toBeNull();

    getVisibleChildNodesSpy.mockRestore();
  });

  it('should wait for lazy-loaded children and select first child when available', () => {
    const mockRootNode = {flowNodeId: 'root', flowNodeInstanceId: 'root-1'};
    const mockInnerInstance = createMockFlowNodeInstance({
      id: 'inner-1',
      flowNodeId: 'adhoc-subprocess',
      type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    });

    const getVisibleChildNodesSpy = vi
      .spyOn(flowNodeInstanceStore, 'getVisibleChildNodes')
      .mockReturnValue([]); // Always return empty for this test

    selectAdHocSubProcessInnerInstance(mockRootNode, mockInnerInstance);

    expect(flowNodeSelectionStore.state.selection).toBeNull();
    expect(getVisibleChildNodesSpy).toHaveBeenCalledWith(mockInnerInstance);

    getVisibleChildNodesSpy.mockRestore();
  });

  it('should handle multiple children and select only the first one', () => {
    const mockRootNode = {flowNodeId: 'root', flowNodeInstanceId: 'root-1'};
    const mockInnerInstance = createMockFlowNodeInstance({
      id: 'inner-1',
      flowNodeId: 'adhoc-subprocess',
      type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    });

    const mockFirstChild = createMockFlowNodeInstance({
      id: 'child-1',
      flowNodeId: 'task-1',
      type: 'SERVICE_TASK',
    });

    const mockSecondChild = createMockFlowNodeInstance({
      id: 'child-2',
      flowNodeId: 'task-2',
      type: 'USER_TASK',
    });

    // Mock multiple children
    const getVisibleChildNodesSpy = vi
      .spyOn(flowNodeInstanceStore, 'getVisibleChildNodes')
      .mockReturnValue([mockFirstChild, mockSecondChild]);

    selectAdHocSubProcessInnerInstance(mockRootNode, mockInnerInstance);

    expect(getVisibleChildNodesSpy).toHaveBeenCalledWith(mockInnerInstance);
    // Should select first child, not second
    expect(flowNodeSelectionStore.state.selection).toEqual({
      flowNodeId: 'task-1',
      flowNodeInstanceId: 'child-1',
    });

    getVisibleChildNodesSpy.mockRestore();
  });
});
