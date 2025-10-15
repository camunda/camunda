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

describe('flowNodeSelection', () => {
  beforeEach(() => {
    flowNodeSelectionStore.reset();
    flowNodeMetaDataStore.reset();
    flowNodeInstanceStore.reset();
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

  it('should select inner instance even when no children exist, then update anchor when child loads', async () => {
    const rootNode = {flowNodeId: 'root', flowNodeInstanceId: 'root-1'};
    const innerInstance = {
      id: 'inner-1',
      type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE' as const,
      state: 'ACTIVE' as const,
      flowNodeId: 'adhoc-subprocess',
      startDate: '2020-08-18T12:07:33.953+0000',
      endDate: null,
      treePath: 'some-tree-path',
      sortValues: ['1606300828415', 'inner-1'] as [string, string],
    };

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'existing',
      flowNodeInstanceId: 'existing-1',
    });

    selectAdHocSubProcessInnerInstance(rootNode, innerInstance);

    expect(flowNodeSelectionStore.state.selection).toEqual({
      flowNodeId: 'adhoc-subprocess',
      flowNodeInstanceId: 'inner-1',
      flowNodeType: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
      anchorFlowNodeId: undefined,
    });

    flowNodeInstanceStore.handleFetchSuccess({
      'some-tree-path': {
        children: [
          {
            id: 'child-1',
            type: 'SERVICE_TASK' as const,
            state: 'ACTIVE' as const,
            flowNodeId: 'task-1',
            startDate: '2020-08-18T12:07:33.953+0000',
            endDate: null,
            treePath: 'child-tree-path',
            sortValues: ['1606300828416', 'child-1'] as [string, string],
          },
        ],
        running: false,
      },
    });

    expect(flowNodeSelectionStore.state.selection).toEqual({
      flowNodeId: 'adhoc-subprocess',
      flowNodeInstanceId: 'inner-1',
      flowNodeType: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
      anchorFlowNodeId: 'task-1',
    });
  });

  it('should select first child as anchor when children exist at selection time', () => {
    const rootNode = {flowNodeId: 'root', flowNodeInstanceId: 'root-1'};
    const innerInstance = {
      id: 'inner-1',
      type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE' as const,
      state: 'ACTIVE' as const,
      flowNodeId: 'adhoc-subprocess',
      startDate: '2020-08-18T12:07:33.953+0000',
      endDate: null,
      treePath: 'some-tree-path',
      sortValues: ['1606300828415', 'inner-1'] as [string, string],
    };

    flowNodeInstanceStore.handleFetchSuccess({
      'some-tree-path': {
        children: [
          {
            id: 'child-1',
            type: 'SERVICE_TASK' as const,
            state: 'ACTIVE' as const,
            flowNodeId: 'task-1',
            startDate: '2020-08-18T12:07:33.953+0000',
            endDate: null,
            treePath: 'child-tree-path',
            sortValues: ['1606300828416', 'child-1'] as [string, string],
          },
        ],
        running: false,
      },
    });

    flowNodeSelectionStore.setSelection(null);

    selectAdHocSubProcessInnerInstance(rootNode, innerInstance);

    expect(flowNodeSelectionStore.state.selection).toEqual({
      flowNodeId: 'adhoc-subprocess',
      flowNodeInstanceId: 'inner-1',
      flowNodeType: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
      anchorFlowNodeId: 'task-1',
    });
  });
});
