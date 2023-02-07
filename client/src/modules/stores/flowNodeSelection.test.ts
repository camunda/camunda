/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processInstanceDetailsStore} from './processInstanceDetails';
import {flowNodeSelectionStore} from './flowNodeSelection';
import {modificationsStore} from './modifications';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {createInstance} from 'modules/testUtils';
import {flowNodeMetaDataStore} from './flowNodeMetaData';
import {
  incidentFlowNodeMetaData,
  singleInstanceMetadata,
} from 'modules/mocks/metadata';
import {processInstanceDetailsStatisticsStore} from './processInstanceDetailsStatistics';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockProcessXml} from 'modules/mocks/mockProcessXml';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {open} from 'modules/mocks/diagrams';

const PROCESS_INSTANCE_ID = '2251799813689404';

describe('stores/flowNodeSelection', () => {
  beforeAll(async () => {
    mockFetchProcessInstance().withSuccess(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
        processName: 'some process name',
      })
    );

    await processInstanceDetailsStore.fetchProcessInstance(PROCESS_INSTANCE_ID);
  });

  afterAll(() => {
    processInstanceDetailsStore.reset();
  });

  beforeEach(() => {
    flowNodeSelectionStore.init();
  });

  afterEach(() => {
    flowNodeSelectionStore.reset();
    modificationsStore.reset();
    processInstanceDetailsDiagramStore.reset();
    processInstanceDetailsStatisticsStore.reset();
    flowNodeMetaDataStore.reset();
  });

  it('should initially select process instance', () => {
    expect(flowNodeSelectionStore.state.selection).toEqual({
      flowNodeId: undefined,
      flowNodeInstanceId: PROCESS_INSTANCE_ID,
      isMultiInstance: false,
    });
  });

  it('should select flow node', () => {
    const selection = {flowNodeId: 'startEvent', isMultiInstance: false};
    const unselectedInstance = {flowNodeId: 'endEvent'};

    flowNodeSelectionStore.selectFlowNode(selection);

    expect(flowNodeSelectionStore.state.selection).toEqual(selection);
    expect(flowNodeSelectionStore.areMultipleInstancesSelected).toBe(true);
    expect(flowNodeSelectionStore.isSelected(selection)).toBe(true);
    expect(flowNodeSelectionStore.isSelected(unselectedInstance)).toBe(false);
  });

  it('should select flow node instance', () => {
    const selection = {
      flowNodeId: 'startEvent',
      flowNodeInstanceId: '2251799813689409',
      isMultiInstance: false,
    };
    const unselectedInstance = {
      flowNodeId: 'startEvent',
      flowNodeInstanceId: '1111111111111111',
      isMultiInstance: false,
    };

    flowNodeSelectionStore.selectFlowNode(selection);

    expect(flowNodeSelectionStore.state.selection).toEqual(selection);
    expect(flowNodeSelectionStore.areMultipleInstancesSelected).toBe(false);
    expect(flowNodeSelectionStore.isSelected(selection)).toBe(true);
    expect(flowNodeSelectionStore.isSelected(unselectedInstance)).toBe(false);
  });

  it('should unselect and fallback to process instance', () => {
    const selection = {flowNodeId: undefined};
    const unselectedInstance = {
      flowNodeId: 'startEvent',
      flowNodeInstanceId: '1111111111111111',
    };

    flowNodeSelectionStore.selectFlowNode(selection);

    expect(flowNodeSelectionStore.areMultipleInstancesSelected).toBe(false);
    expect(flowNodeSelectionStore.isSelected(unselectedInstance)).toBe(false);
    expect(flowNodeSelectionStore.state.selection).toEqual({
      flowNodeInstanceId: PROCESS_INSTANCE_ID,
      isMultiInstance: false,
    });
  });

  it('should select multi instance flow node', () => {
    const selection = {
      flowNodeId: 'subProcess',
      isMultiInstance: true,
    };

    const unselectedInstance = {
      flowNodeId: 'subProcess',
      isMultiInstance: false,
    };

    flowNodeSelectionStore.selectFlowNode(selection);

    expect(flowNodeSelectionStore.state.selection).toEqual(selection);
    expect(flowNodeSelectionStore.isSelected(selection)).toBe(true);
    expect(flowNodeSelectionStore.isSelected(unselectedInstance)).toBe(false);
  });

  it('should select non-multi instance flow node', () => {
    const selection = {
      flowNodeId: 'subProcess',
      isMultiInstance: false,
    };

    const unselectedInstance = {
      flowNodeId: 'subProcess',
      isMultiInstance: true,
    };

    flowNodeSelectionStore.selectFlowNode(selection);

    expect(flowNodeSelectionStore.state.selection).toEqual(selection);
    expect(flowNodeSelectionStore.isSelected(selection)).toBe(true);
    expect(flowNodeSelectionStore.isSelected(unselectedInstance)).toBe(false);
  });

  it('should fallback to process instance when selecting twice', () => {
    const selection = {
      flowNodeId: 'startEvent',
      flowNodeInstanceId: '2251799813689409',
    };
    const unselectedInstance = {
      flowNodeId: 'startEvent',
      flowNodeInstanceId: '1111111111111111',
    };

    flowNodeSelectionStore.selectFlowNode(selection);
    flowNodeSelectionStore.selectFlowNode(selection);

    expect(flowNodeSelectionStore.areMultipleInstancesSelected).toBe(false);
    expect(flowNodeSelectionStore.isSelected(unselectedInstance)).toBe(false);
    expect(flowNodeSelectionStore.state.selection).toEqual({
      flowNodeInstanceId: PROCESS_INSTANCE_ID,
      isMultiInstance: false,
    });
  });

  it('should clear selection when modification mode is enabled/disabled', async () => {
    const rootNode = {
      flowNodeInstanceId: PROCESS_INSTANCE_ID,
      isMultiInstance: false,
    };
    const selection = {
      flowNodeId: 'startEvent',
      flowNodeInstanceId: '2251799813689409',
    };

    flowNodeSelectionStore.selectFlowNode(selection);
    expect(flowNodeSelectionStore.state.selection).toEqual(selection);

    modificationsStore.enableModificationMode();
    expect(flowNodeSelectionStore.state.selection).toEqual(rootNode);

    flowNodeSelectionStore.selectFlowNode(selection);
    expect(flowNodeSelectionStore.state.selection).toEqual(selection);

    modificationsStore.disableModificationMode();
    expect(flowNodeSelectionStore.state.selection).toEqual(rootNode);
  });

  it('should get selected flow node name', async () => {
    const selection = {
      flowNodeId: 'startEvent',
      flowNodeInstanceId: '2251799813689409',
    };

    expect(flowNodeSelectionStore.selectedFlowNodeName).toBe(
      'some process name'
    );

    flowNodeSelectionStore.selectFlowNode(selection);
    expect(flowNodeSelectionStore.selectedFlowNodeName).toBe('startEvent');
  });

  it('should clear selection when last modification is removed which results in selected scope being removed', () => {
    const rootNode = {
      flowNodeInstanceId: PROCESS_INSTANCE_ID,
      isMultiInstance: false,
    };

    const selectionForNewAddedTokenScope = {
      flowNodeId: 'someFlowNode',
      flowNodeInstanceId: 'scope-for-add-token',
    };

    const selectionForNewAddedTokenParentScope = {
      flowNodeId: 'someFlowNode',
      flowNodeInstanceId: 'some-parent-scope-for-add-token',
    };

    const selectionForNewMovedTokenFirstScope = {
      flowNodeId: 'someTargetFlowNode',
      flowNodeInstanceId: 'scope-for-move-token-1',
    };

    const selectionForNewMovedTokenSecondScope = {
      flowNodeId: 'someTargetFlowNode',
      flowNodeInstanceId: 'scope-for-move-token-2',
    };

    const selectionForNewMovedTokenParentScope = {
      flowNodeId: 'someParentFlowNode',
      flowNodeInstanceId: 'some-parent-scope-for-move-token',
    };

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: 'scope-for-add-token',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        flowNode: {id: 'someFlowNode', name: 'some flow node'},
        parentScopeIds: {},
      },
    });

    flowNodeSelectionStore.selectFlowNode(selectionForNewAddedTokenScope);
    expect(flowNodeSelectionStore.state.selection).toEqual(
      selectionForNewAddedTokenScope
    );

    modificationsStore.removeLastModification();
    expect(flowNodeSelectionStore.state.selection).toEqual(rootNode);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: 'scope-for-add-token',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        flowNode: {id: 'someFlowNode', name: 'some flow node'},
        parentScopeIds: {someParentFlowNode: 'some-parent-scope-for-add-token'},
      },
    });

    flowNodeSelectionStore.selectFlowNode(selectionForNewAddedTokenParentScope);
    expect(flowNodeSelectionStore.state.selection).toEqual(
      selectionForNewAddedTokenParentScope
    );

    modificationsStore.removeLastModification();
    expect(flowNodeSelectionStore.state.selection).toEqual(rootNode);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        flowNode: {id: 'someFlowNode', name: 'some flow node'},
        targetFlowNode: {
          id: 'someTargetFlowNode',
          name: 'some target flow node',
        },
        scopeIds: ['scope-for-move-token-1', 'scope-for-move-token-2'],
        parentScopeIds: {},
      },
    });

    flowNodeSelectionStore.selectFlowNode(selectionForNewMovedTokenFirstScope);
    expect(flowNodeSelectionStore.state.selection).toEqual(
      selectionForNewMovedTokenFirstScope
    );
    modificationsStore.removeLastModification();
    expect(flowNodeSelectionStore.state.selection).toEqual(rootNode);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        flowNode: {id: 'someFlowNode', name: 'some flow node'},
        targetFlowNode: {
          id: 'someTargetFlowNode',
          name: 'some target flow node',
        },
        scopeIds: ['scope-for-move-token-1', 'scope-for-move-token-2'],
        parentScopeIds: {},
      },
    });

    flowNodeSelectionStore.selectFlowNode(selectionForNewMovedTokenSecondScope);
    expect(flowNodeSelectionStore.state.selection).toEqual(
      selectionForNewMovedTokenSecondScope
    );
    modificationsStore.removeLastModification();
    expect(flowNodeSelectionStore.state.selection).toEqual(rootNode);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        flowNode: {id: 'someFlowNode', name: 'some flow node'},
        targetFlowNode: {
          id: 'someTargetFlowNode',
          name: 'some target flow node',
        },
        scopeIds: ['scope-for-move-token-1', 'scope-for-move-token-2'],
        parentScopeIds: {
          someParentFlowNode: 'some-parent-scope-for-move-token',
        },
      },
    });

    flowNodeSelectionStore.selectFlowNode(selectionForNewMovedTokenParentScope);
    expect(flowNodeSelectionStore.state.selection).toEqual(
      selectionForNewMovedTokenParentScope
    );
    modificationsStore.removeLastModification();
    expect(flowNodeSelectionStore.state.selection).toEqual(rootNode);
  });

  it('should clear selection when specific flow node modification is removed which results in selected scope being removed', () => {
    const rootNode = {
      flowNodeInstanceId: PROCESS_INSTANCE_ID,
      isMultiInstance: false,
    };

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: 'scope-for-add-token',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        flowNode: {id: 'someFlowNode', name: 'some flow node'},
        parentScopeIds: {},
      },
    });

    let selection = {
      flowNodeId: 'someFlowNode',
      flowNodeInstanceId: 'scope-for-add-token',
    };

    flowNodeSelectionStore.selectFlowNode(selection);
    expect(flowNodeSelectionStore.state.selection).toEqual(selection);

    modificationsStore.removeFlowNodeModification({
      operation: 'ADD_TOKEN',
      scopeId: 'scope-for-add-token',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      flowNode: {id: 'someFlowNode', name: 'some flow node'},
      parentScopeIds: {},
    });
    expect(flowNodeSelectionStore.state.selection).toEqual(rootNode);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: 'scope-for-add-token',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        flowNode: {id: 'someFlowNode', name: 'some flow node'},
        parentScopeIds: {someParentFlowNode: 'some-parent-scope-for-add-token'},
      },
    });

    selection = {
      flowNodeId: 'someFlowNode',
      flowNodeInstanceId: 'some-parent-scope-for-add-token',
    };

    flowNodeSelectionStore.selectFlowNode(selection);
    expect(flowNodeSelectionStore.state.selection).toEqual(selection);

    modificationsStore.removeFlowNodeModification({
      operation: 'ADD_TOKEN',
      scopeId: 'scope-for-add-token',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      flowNode: {id: 'someFlowNode', name: 'some flow node'},
      parentScopeIds: {someParentFlowNode: 'some-parent-scope-for-add-token'},
    });
    expect(flowNodeSelectionStore.state.selection).toEqual(rootNode);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        flowNode: {id: 'someFlowNode', name: 'some flow node'},
        targetFlowNode: {
          id: 'someTargetFlowNode',
          name: 'some target flow node',
        },
        scopeIds: ['scope-for-move-token-1', 'scope-for-move-token-2'],
        parentScopeIds: {},
      },
    });

    selection = {
      flowNodeId: 'someTargetFlowNode',
      flowNodeInstanceId: 'scope-for-move-token-1',
    };

    flowNodeSelectionStore.selectFlowNode(selection);
    expect(flowNodeSelectionStore.state.selection).toEqual(selection);
    modificationsStore.removeFlowNodeModification({
      operation: 'MOVE_TOKEN',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      flowNode: {id: 'someFlowNode', name: 'some flow node'},
      targetFlowNode: {
        id: 'someTargetFlowNode',
        name: 'some target flow node',
      },
      scopeIds: ['scope-for-move-token-1', 'scope-for-move-token-2'],
      parentScopeIds: {},
    });
    expect(flowNodeSelectionStore.state.selection).toEqual(rootNode);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        flowNode: {id: 'someFlowNode', name: 'some flow node'},
        targetFlowNode: {
          id: 'someTargetFlowNode',
          name: 'some target flow node',
        },
        scopeIds: ['scope-for-move-token-1', 'scope-for-move-token-2'],
        parentScopeIds: {},
      },
    });

    selection = {
      flowNodeId: 'someTargetFlowNode',
      flowNodeInstanceId: 'scope-for-move-token-2',
    };

    flowNodeSelectionStore.selectFlowNode(selection);
    expect(flowNodeSelectionStore.state.selection).toEqual(selection);
    modificationsStore.removeFlowNodeModification({
      operation: 'MOVE_TOKEN',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      flowNode: {id: 'someFlowNode', name: 'some flow node'},
      targetFlowNode: {
        id: 'someTargetFlowNode',
        name: 'some target flow node',
      },
      scopeIds: ['scope-for-move-token-1', 'scope-for-move-token-2'],
      parentScopeIds: {},
    });
    expect(flowNodeSelectionStore.state.selection).toEqual(rootNode);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        flowNode: {id: 'someFlowNode', name: 'some flow node'},
        targetFlowNode: {
          id: 'someTargetFlowNode',
          name: 'some target flow node',
        },
        scopeIds: ['scope-for-move-token-1', 'scope-for-move-token-2'],
        parentScopeIds: {
          someParentFlowNode: 'some-parent-scope-for-move-token',
        },
      },
    });

    selection = {
      flowNodeId: 'someParentFlowNode',
      flowNodeInstanceId: 'some-parent-scope-for-move-token',
    };

    flowNodeSelectionStore.selectFlowNode(selection);
    expect(flowNodeSelectionStore.state.selection).toEqual(selection);
    modificationsStore.removeFlowNodeModification({
      operation: 'MOVE_TOKEN',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      flowNode: {id: 'someFlowNode', name: 'some flow node'},
      targetFlowNode: {
        id: 'someTargetFlowNode',
        name: 'some target flow node',
      },
      scopeIds: ['scope-for-move-token-1', 'scope-for-move-token-2'],
      parentScopeIds: {
        someParentFlowNode: 'some-parent-scope-for-move-token',
      },
    });
    expect(flowNodeSelectionStore.state.selection).toEqual(rootNode);
  });

  it('should get selected running instance count', async () => {
    mockFetchProcessXML().withSuccess(open('diagramForModifications.bpmn'));
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'StartEvent_1',
        active: 2,
        incidents: 1,
        completed: 1,
        canceled: 0,
      },
    ]);

    await processInstanceDetailsDiagramStore.fetchProcessXml('some-process-id');
    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(
      'instance_id'
    );

    // empty selection
    flowNodeSelectionStore.setSelection(null);
    expect(flowNodeSelectionStore.selectedRunningInstanceCount).toBe(0);
    flowNodeSelectionStore.setSelection({});
    expect(flowNodeSelectionStore.selectedRunningInstanceCount).toBe(0);

    // select root node
    flowNodeSelectionStore.clearSelection();
    expect(flowNodeSelectionStore.selectedRunningInstanceCount).toBe(0);

    // select placeholder
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'some-flownode-id',
      flowNodeInstanceId: 'some-instance-id',
      isPlaceholder: true,
    });

    expect(flowNodeSelectionStore.selectedRunningInstanceCount).toBe(0);

    // select single running flow node instance id
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'StartEvent_1',
      flowNodeInstanceId: 'some-running-instance-id',
    });

    flowNodeMetaDataStore.setMetaData(incidentFlowNodeMetaData);
    expect(flowNodeSelectionStore.selectedRunningInstanceCount).toBe(1);

    // select single completed flow node instance id
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'StartEvent_1',
      flowNodeInstanceId: 'some-completed-instance-id',
    });

    flowNodeMetaDataStore.setMetaData(singleInstanceMetadata);
    expect(flowNodeSelectionStore.selectedRunningInstanceCount).toBe(0);

    // select flow node
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'StartEvent_1',
    });

    expect(flowNodeSelectionStore.selectedRunningInstanceCount).toBe(3);
  });

  it('should get has pending cancel modification', async () => {
    mockFetchProcessXML().withSuccess(mockProcessXml);
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'userTask',
        active: 2,
        incidents: 1,
        completed: 1,
        canceled: 0,
      },
    ]);

    await processInstanceDetailsDiagramStore.fetchProcessXml('some-process-id');
    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(
      'instance_id'
    );

    // cancel all tokens
    modificationsStore.cancelAllTokens('userTask');

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
      flowNodeInstanceId: '2251799813689409',
    });
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      true
    );
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
    });
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      true
    );
    flowNodeSelectionStore.clearSelection();
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      true
    );

    modificationsStore.removeLastModification();

    // cancel all tokens one by one
    modificationsStore.cancelToken('userTask', '2251799813689409');
    modificationsStore.cancelToken('userTask', '2251799813689410');
    modificationsStore.cancelToken('userTask', '2251799813689411');

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
      flowNodeInstanceId: '2251799813689409',
    });

    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      true
    );
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
    });
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      true
    );
    flowNodeSelectionStore.clearSelection();

    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      true
    );

    modificationsStore.removeLastModification();
    modificationsStore.removeLastModification();
    modificationsStore.removeLastModification();

    // cancel one token
    modificationsStore.cancelToken('userTask', '2251799813689409');

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
      flowNodeInstanceId: '2251799813689409',
    });

    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      true
    );

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
      flowNodeInstanceId: '2251799813689410',
    });
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      false
    );

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
      flowNodeInstanceId: '2251799813689411',
    });
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      false
    );

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
    });
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      false
    );
    flowNodeSelectionStore.clearSelection();
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      false
    );
  });

  it('should get has pending move modification', async () => {
    mockFetchProcessXML().withSuccess(mockProcessXml);
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'userTask',
        active: 2,
        incidents: 1,
        completed: 1,
        canceled: 0,
      },
    ]);

    await processInstanceDetailsDiagramStore.fetchProcessXml('some-process-id');
    await processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(
      'instance_id'
    );

    // move all tokens
    modificationsStore.addMoveModification({
      sourceFlowNodeId: 'userTask',
      targetFlowNodeId: 'anotherTask',
      affectedTokenCount: 3,
      visibleAffectedTokenCount: 3,
      newScopeCount: 3,
    });

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
      flowNodeInstanceId: '2251799813689409',
    });
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      true
    );
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
    });
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      true
    );
    flowNodeSelectionStore.clearSelection();
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      false
    );

    modificationsStore.removeLastModification();

    // move all tokens one by one
    modificationsStore.addMoveModification({
      sourceFlowNodeId: 'userTask',
      sourceFlowNodeInstanceKey: '2251799813689409',
      targetFlowNodeId: 'anotherTask',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      newScopeCount: 1,
    });
    modificationsStore.addMoveModification({
      sourceFlowNodeId: 'userTask',
      sourceFlowNodeInstanceKey: '2251799813689410',
      targetFlowNodeId: 'anotherTask',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      newScopeCount: 1,
    });
    modificationsStore.addMoveModification({
      sourceFlowNodeId: 'userTask',
      sourceFlowNodeInstanceKey: '2251799813689411',
      targetFlowNodeId: 'anotherTask',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      newScopeCount: 1,
    });

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
      flowNodeInstanceId: '2251799813689409',
    });

    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      true
    );
    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
    });
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      true
    );
    flowNodeSelectionStore.clearSelection();
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      false
    );

    modificationsStore.removeLastModification();
    modificationsStore.removeLastModification();
    modificationsStore.removeLastModification();

    // move one token
    modificationsStore.addMoveModification({
      sourceFlowNodeId: 'userTask',
      sourceFlowNodeInstanceKey: '2251799813689409',
      targetFlowNodeId: 'anotherTask',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      newScopeCount: 1,
    });

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
      flowNodeInstanceId: '2251799813689409',
    });

    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      true
    );

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
      flowNodeInstanceId: '2251799813689410',
    });

    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      false
    );

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
      flowNodeInstanceId: '2251799813689411',
    });
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      false
    );

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'userTask',
    });
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      false
    );
    flowNodeSelectionStore.clearSelection();
    expect(flowNodeSelectionStore.hasPendingCancelOrMoveModification).toBe(
      false
    );
  });
});
