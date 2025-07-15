/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {modificationsStore, type FlowNodeModification} from './modifications';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {
  createAddVariableModification,
  createEditVariableModification,
} from 'modules/mocks/modifications';
import {processInstanceDetailsStore} from './processInstanceDetails';
import {createInstance} from 'modules/testUtils';
import {
  cancelAllTokens,
  finishMovingToken,
  generateParentScopeIds,
} from 'modules/utils/modifications';
import {mockNestedSubProcessBusinessObjects} from 'modules/mocks/mockNestedSubProcessBusinessObjects';

type AddModificationPayload = Extract<
  FlowNodeModification['payload'],
  {operation: 'ADD_TOKEN'}
>;

describe('stores/modifications', () => {
  afterEach(() => {
    modificationsStore.reset();
    processInstanceDetailsStore.reset();
  });

  it('should enable/disable modification mode', async () => {
    expect(modificationsStore.isModificationModeEnabled).toBe(false);
    modificationsStore.enableModificationMode();
    expect(modificationsStore.isModificationModeEnabled).toBe(true);
    modificationsStore.disableModificationMode();
    expect(modificationsStore.isModificationModeEnabled).toBe(false);
    modificationsStore.enableModificationMode();
    expect(modificationsStore.isModificationModeEnabled).toBe(true);
    modificationsStore.startApplyingModifications();
    expect(modificationsStore.isModificationModeEnabled).toBe(false);
  });

  it('should add/remove flow node modifications', async () => {
    const uniqueID = generateUniqueID();
    const uniqueIDForMove = generateUniqueID();
    expect(modificationsStore.state.modifications).toEqual([]);
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: uniqueID,
        flowNode: {id: 'service-task-1', name: 'service-task-1'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(1);
    cancelAllTokens('service-task-2', 3, 3, {});

    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: 'service-task-3', name: 'service-task-3'},
        targetFlowNode: {id: 'service-task-4', name: 'service-task-4'},
        affectedTokenCount: 2,
        visibleAffectedTokenCount: 2,
        scopeIds: [uniqueIDForMove],
        parentScopeIds: {},
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(3);

    expect(modificationsStore.flowNodeModifications).toEqual([
      {
        operation: 'ADD_TOKEN',
        scopeId: uniqueID,
        flowNode: {id: 'service-task-1', name: 'service-task-1'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
      {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: 'service-task-2', name: 'service-task-2'},
        affectedTokenCount: 3,
        visibleAffectedTokenCount: 3,
      },
      {
        operation: 'MOVE_TOKEN',
        flowNode: {id: 'service-task-3', name: 'service-task-3'},
        targetFlowNode: {id: 'service-task-4', name: 'service-task-4'},
        affectedTokenCount: 2,
        visibleAffectedTokenCount: 2,
        scopeIds: [uniqueIDForMove],
        parentScopeIds: {},
      },
    ]);

    modificationsStore.removeFlowNodeModification({
      flowNode: {id: 'non-existing-flow-node', name: ''},
      operation: 'ADD_TOKEN',
      scopeId: '1',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      parentScopeIds: {},
    });
    expect(modificationsStore.state.modifications.length).toEqual(3);
    modificationsStore.removeFlowNodeModification({
      flowNode: {id: 'service-task-4', name: ''},
      operation: 'ADD_TOKEN',
      scopeId: '1',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      parentScopeIds: {},
    });
    expect(modificationsStore.state.modifications.length).toEqual(3);
    modificationsStore.removeFlowNodeModification({
      flowNode: {id: 'service-task-2', name: ''},
      operation: 'ADD_TOKEN',
      scopeId: '2',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      parentScopeIds: {},
    });
    expect(modificationsStore.state.modifications.length).toEqual(3);
    modificationsStore.removeFlowNodeModification({
      flowNode: {id: 'service-task-2', name: ''},
      operation: 'CANCEL_TOKEN',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
    });
    expect(modificationsStore.state.modifications.length).toEqual(2);
    modificationsStore.removeFlowNodeModification({
      flowNode: {id: 'service-task-1', name: ''},
      operation: 'ADD_TOKEN',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      scopeId: uniqueID,
      parentScopeIds: {},
    });
    expect(modificationsStore.state.modifications.length).toEqual(1);

    expect(modificationsStore.flowNodeModifications).toEqual([
      {
        operation: 'MOVE_TOKEN',
        flowNode: {id: 'service-task-3', name: 'service-task-3'},
        targetFlowNode: {id: 'service-task-4', name: 'service-task-4'},
        affectedTokenCount: 2,
        visibleAffectedTokenCount: 2,
        scopeIds: [uniqueIDForMove],
        parentScopeIds: {},
      },
    ]);
  });

  it('should add/remove variable modifications', async () => {
    expect(modificationsStore.state.modifications).toEqual([]);
    createAddVariableModification({
      id: '1',
      scopeId: '1',
      flowNodeName: 'flow-node-1',
      name: 'variable1',
      value: 'variable1-newValue',
    });

    expect(modificationsStore.state.modifications.length).toEqual(1);
    createEditVariableModification({
      name: 'variable1',
      oldValue: 'variable2-oldValue',
      newValue: 'variable2-newValue',
      flowNodeName: 'flow-node-2',
      scopeId: '2',
    });

    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.removeVariableModification(
      'non-existing-variable',
      'variable1',
      'EDIT_VARIABLE',
      'variables',
    );
    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.removeVariableModification(
      '1',
      'non-existing-variable-name',
      'ADD_VARIABLE',
      'variables',
    );
    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.removeVariableModification(
      '1',
      '1',
      'ADD_VARIABLE',
      'variables',
    );
    expect(modificationsStore.state.modifications.length).toEqual(1);

    expect(modificationsStore.state.lastRemovedModification).toEqual({
      modification: {
        payload: {
          flowNodeName: 'flow-node-1',
          id: '1',
          name: 'variable1',
          newValue: 'variable1-newValue',
          operation: 'ADD_VARIABLE',
          scopeId: '1',
        },
        type: 'variable',
      },
      source: 'variables',
    });
    expect(modificationsStore.state.modifications).toEqual([
      {
        payload: {
          flowNodeName: 'flow-node-2',
          id: 'variable1',
          scopeId: '2',
          name: 'variable1',
          newValue: 'variable2-newValue',
          oldValue: 'variable2-oldValue',
          operation: 'EDIT_VARIABLE',
        },
        type: 'variable',
      },
    ]);

    modificationsStore.removeVariableModification(
      '2',
      'variable1',
      'EDIT_VARIABLE',
      'variables',
    );

    expect(modificationsStore.state.lastRemovedModification).toEqual({
      modification: {
        payload: {
          flowNodeName: 'flow-node-2',
          id: 'variable1',
          name: 'variable1',
          newValue: 'variable2-newValue',
          oldValue: 'variable2-oldValue',
          operation: 'EDIT_VARIABLE',
          scopeId: '2',
        },
        type: 'variable',
      },
      source: 'variables',
    });

    expect(modificationsStore.state.modifications.length).toEqual(0);
  });

  it('should remove last modification', async () => {
    const uniqueID = generateUniqueID();

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: uniqueID,
        flowNode: {id: 'service-task-1', name: 'service-task-1'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });

    cancelAllTokens('service-task-2', 3, 3, {});

    expect(modificationsStore.lastModification).toEqual({
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: 'service-task-2', name: 'service-task-2'},
        affectedTokenCount: 3,
        visibleAffectedTokenCount: 3,
      },
      type: 'token',
    });

    modificationsStore.removeLastModification();

    expect(modificationsStore.lastModification).toEqual({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        flowNode: {id: 'service-task-1', name: 'service-task-1'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        scopeId: uniqueID,
        parentScopeIds: {},
      },
    });

    modificationsStore.removeLastModification();

    expect(modificationsStore.lastModification).toEqual(undefined);
  });

  it('should move tokens', async () => {
    expect(
      modificationsStore.state.sourceFlowNodeIdForMoveOperation,
    ).toBeNull();

    modificationsStore.startMovingToken('StartEvent_1');
    expect(modificationsStore.state.sourceFlowNodeIdForMoveOperation).toBe(
      'StartEvent_1',
    );

    finishMovingToken(2, 2, {}, 'end-event');

    expect(
      modificationsStore.state.sourceFlowNodeIdForMoveOperation,
    ).toBeNull();
    expect(modificationsStore.state.status).toBe('enabled');
  });

  it('should retrieve variable modifications', () => {
    createAddVariableModification({
      scopeId: 'flow-node-1',
      flowNodeName: 'flowNode1',
      id: '1',
      name: 'name1',
      value: 'value1',
    });

    createAddVariableModification({
      scopeId: 'flow-node-1',
      flowNodeName: 'flowNode1',
      id: '1',
      name: 'name1',
      value: 'value2',
    });

    expect(modificationsStore.variableModifications).toEqual([
      {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: '1',
        name: 'name1',
        newValue: 'value2',
      },
    ]);

    createAddVariableModification({
      scopeId: 'flow-node-1',
      flowNodeName: 'flowNode1',
      id: '1',
      name: 'name2',
      value: 'value3',
    });

    expect(modificationsStore.variableModifications).toEqual([
      {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: '1',
        name: 'name2',
        newValue: 'value3',
      },
    ]);

    createEditVariableModification({
      scopeId: 'flow-node-1',
      flowNodeName: 'flowNode1',
      name: 'existing-variable',
      oldValue: '12',
      newValue: '123',
    });

    createEditVariableModification({
      scopeId: 'flow-node-1',
      flowNodeName: 'flowNode1',
      name: 'existing-variable',
      oldValue: '12',
      newValue: '1234',
    });

    expect(modificationsStore.variableModifications).toEqual([
      {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: '1',
        name: 'name2',
        newValue: 'value3',
      },
      {
        operation: 'EDIT_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: 'existing-variable',
        name: 'existing-variable',
        newValue: '1234',
        oldValue: '12',
      },
    ]);

    createAddVariableModification({
      scopeId: 'flow-node-2',
      flowNodeName: 'flowNode2',
      id: '1',
      name: 'name2',
      value: 'value3',
    });

    expect(modificationsStore.variableModifications).toEqual([
      {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: '1',
        name: 'name2',
        newValue: 'value3',
      },
      {
        operation: 'EDIT_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: 'existing-variable',
        name: 'existing-variable',
        newValue: '1234',
        oldValue: '12',
      },
      {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-2',
        flowNodeName: 'flowNode2',
        id: '1',
        name: 'name2',
        newValue: 'value3',
      },
    ]);
  });

  it('should not generate parent scope id twice', async () => {
    processInstanceDetailsStore.setProcessInstance(
      createInstance({bpmnProcessId: 'nested_sub_process'}),
    );

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        flowNode: {id: 'user_task', name: 'User Task'},
        scopeId: 'random-scope-id-0',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: generateParentScopeIds(
          mockNestedSubProcessBusinessObjects,
          'user_task',
          'nested_sub_process',
        ),
      },
    });
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        flowNode: {id: 'user_task', name: 'User Task'},
        scopeId: 'random-scope-id-1',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: generateParentScopeIds(
          mockNestedSubProcessBusinessObjects,
          'user_task',
          'nested_sub_process',
        ),
      },
    });

    const [firstModification, secondModification] =
      modificationsStore.flowNodeModifications as AddModificationPayload[];

    expect(firstModification?.parentScopeIds).toEqual({
      inner_sub_process: expect.any(String),
      parent_sub_process: expect.any(String),
    });

    expect(secondModification?.parentScopeIds).toEqual({});
  });

  it('should retrieve "edit variable" modifications', async () => {
    const FLOW_NODE_INSTANCE_ID = 'test-flow-node';

    createEditVariableModification({
      scopeId: FLOW_NODE_INSTANCE_ID,
      name: 'test',
      oldValue: '12',
      newValue: '123',
    });
    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        'test',
        'EDIT_VARIABLE',
      ),
    ).toEqual({
      operation: 'EDIT_VARIABLE',
      flowNodeName: 'flow-node-name',
      id: 'test',
      scopeId: FLOW_NODE_INSTANCE_ID,
      name: 'test',
      oldValue: '12',
      newValue: '123',
    });

    createEditVariableModification({
      scopeId: FLOW_NODE_INSTANCE_ID,
      name: 'test2',
      oldValue: '123',
      newValue: '1234',
    });
    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        'test2',
        'EDIT_VARIABLE',
      ),
    ).toEqual({
      operation: 'EDIT_VARIABLE',
      flowNodeName: 'flow-node-name',
      id: 'test2',
      scopeId: FLOW_NODE_INSTANCE_ID,
      name: 'test2',
      oldValue: '123',
      newValue: '1234',
    });

    createEditVariableModification({
      scopeId: FLOW_NODE_INSTANCE_ID,
      name: 'test',
      oldValue: '12',
      newValue: '12345',
    });
    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        'test',
        'EDIT_VARIABLE',
      ),
    ).toEqual({
      operation: 'EDIT_VARIABLE',
      flowNodeName: 'flow-node-name',
      id: 'test',
      scopeId: FLOW_NODE_INSTANCE_ID,
      name: 'test',
      oldValue: '12',
      newValue: '12345',
    });
  });

  it('should retrieve "add variable" modifications', async () => {
    const FLOW_NODE_INSTANCE_ID = 'test-flow-node';

    expect(modificationsStore.state.modifications).toEqual([]);
    createAddVariableModification({
      scopeId: FLOW_NODE_INSTANCE_ID,
      id: '1',
      name: 'test',
      value: '123',
    });

    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        '1',
        'ADD_VARIABLE',
      ),
    ).toEqual({
      operation: 'ADD_VARIABLE',
      scopeId: FLOW_NODE_INSTANCE_ID,
      flowNodeName: 'flow-node-name',
      id: '1',
      name: 'test',
      newValue: '123',
    });
    expect(
      modificationsStore.getAddVariableModifications(FLOW_NODE_INSTANCE_ID),
    ).toEqual([
      {
        id: '1',
        name: 'test',
        value: '123',
      },
    ]);

    createAddVariableModification({
      scopeId: FLOW_NODE_INSTANCE_ID,
      id: '1',
      name: 'test',
      value: '1234',
    });

    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        '1',
        'ADD_VARIABLE',
      ),
    ).toEqual({
      operation: 'ADD_VARIABLE',
      scopeId: FLOW_NODE_INSTANCE_ID,
      flowNodeName: 'flow-node-name',
      id: '1',
      name: 'test',
      newValue: '1234',
    });
    expect(
      modificationsStore.getAddVariableModifications(FLOW_NODE_INSTANCE_ID),
    ).toEqual([
      {
        id: '1',
        name: 'test',
        value: '1234',
      },
    ]);

    createAddVariableModification({
      scopeId: FLOW_NODE_INSTANCE_ID,
      id: '1',
      name: 'test-updated',
      value: '1234',
    });

    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        '1',
        'ADD_VARIABLE',
      ),
    ).toEqual({
      operation: 'ADD_VARIABLE',
      scopeId: FLOW_NODE_INSTANCE_ID,
      flowNodeName: 'flow-node-name',
      id: '1',
      name: 'test-updated',
      newValue: '1234',
    });
    expect(
      modificationsStore.getAddVariableModifications(FLOW_NODE_INSTANCE_ID),
    ).toEqual([
      {
        id: '1',
        name: 'test-updated',
        value: '1234',
      },
    ]);

    createAddVariableModification({
      scopeId: FLOW_NODE_INSTANCE_ID,
      id: '2',
      name: 'another-variable',
      value: '987',
    });

    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        '1',
        'ADD_VARIABLE',
      ),
    ).toEqual({
      operation: 'ADD_VARIABLE',
      scopeId: FLOW_NODE_INSTANCE_ID,
      flowNodeName: 'flow-node-name',
      id: '1',
      name: 'test-updated',
      newValue: '1234',
    });
    expect(
      modificationsStore.getLastVariableModification(
        FLOW_NODE_INSTANCE_ID,
        '2',
        'ADD_VARIABLE',
      ),
    ).toEqual({
      operation: 'ADD_VARIABLE',
      scopeId: FLOW_NODE_INSTANCE_ID,
      flowNodeName: 'flow-node-name',
      id: '2',
      name: 'another-variable',
      newValue: '987',
    });
    expect(
      modificationsStore.getAddVariableModifications(FLOW_NODE_INSTANCE_ID),
    ).toEqual([
      {
        id: '1',
        name: 'test-updated',
        value: '1234',
      },
      {
        id: '2',
        name: 'another-variable',
        value: '987',
      },
    ]);

    expect(modificationsStore.getAddVariableModifications(null)).toEqual([]);
    expect(
      modificationsStore.getAddVariableModifications('non-existing-flow-node'),
    ).toEqual([]);
  });

  it('should generate modifications payload', () => {
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        flowNode: {id: 'flow_node_0', name: 'flow node 0'},
        scopeId: 'random-scope-id-0',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        flowNode: {id: 'flow_node_1', name: 'flow node 1'},
        scopeId: 'random-scope-id-1',
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        parentScopeIds: {
          'first-parent-scope': 'random-scope-id-first',
          'second-parent-scope': 'random-scope-id-second',
        },
      },
    });
    cancelAllTokens('flow_node_2', 0, 0, {});

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: 'flow_node_3', name: 'flow node 3'},
        targetFlowNode: {id: 'flow_node_4', name: 'flow node 4'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        scopeIds: ['random-scope-id-2'],
        parentScopeIds: {
          'first-parent': 'random-scope-id-for-parent-1',
          'second-parent': 'random-scope-id-for-parent-2',
          'third-parent': 'random-scope-id-for-parent-3',
        },
      },
    });

    // add 2 variables to one of the newly created scopes
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-1',
        name: 'name1',
        newValue: '"value1"',
        id: '1',
        flowNodeName: 'flow node 1',
      },
    });
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-1',
        name: 'name2',
        newValue: '"value2"',
        id: '2',
        flowNodeName: 'flow node 1',
      },
    });

    // add 2 variables and edit one variable to existing scopes
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-5',
        name: 'name3',
        newValue: '"value3"',
        id: '3',
        flowNodeName: 'flow node 5',
      },
    });
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-5',
        name: 'name4',
        newValue: '"value4"',
        id: '4',
        flowNodeName: 'flow node 5',
      },
    });
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'EDIT_VARIABLE',
        scopeId: 'random-scope-id-5',
        name: 'name5',
        oldValue: '"value5"',
        newValue: '"value5-edited"',
        id: '5',
        flowNodeName: 'flow node 5',
      },
    });
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'EDIT_VARIABLE',
        scopeId: 'random-scope-id-5',
        name: 'name5',
        oldValue: '"value5"',
        newValue: '"value5-edited2"',
        id: '5',
        flowNodeName: 'flow node 5',
      },
    });

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-2',
        name: 'name6',
        newValue: '"value6"',
        id: '6',
        flowNodeName: 'flow node 4',
      },
    });

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-for-parent-1',
        name: 'name7',
        newValue: '"value7"',
        id: '7',
        flowNodeName: 'flow node 7',
      },
    });

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-for-parent-1',
        name: 'name8',
        newValue: '"value8"',
        id: '8',
        flowNodeName: 'flow node 7',
      },
    });

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-for-parent-3',
        name: 'name9',
        newValue: '"value9"',
        id: '9',
        flowNodeName: 'flow node 9',
      },
    });

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'random-scope-id-first',
        name: 'name10',
        newValue: '"value10"',
        id: '10',
        flowNodeName: 'flow node 10',
      },
    });

    modificationsStore.cancelToken('flow_node_5', 'some_instance_key', {});
    modificationsStore.addMoveModification({
      sourceFlowNodeId: 'flow_node_6',
      sourceFlowNodeInstanceKey: 'some_instance_key_2',
      targetFlowNodeId: 'flow_node_7',
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
      newScopeCount: 1,
      businessObjects: {},
      bpmnProcessId: 'inner_sub_process',
    });

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        flowNode: {id: 'flow_node_11', name: 'flow node 11'},
        scopeId: 'random-scope-id-11',
        affectedTokenCount: 1,
        ancestorElement: {
          instanceKey: 'some-ancestor-instance-key',
          flowNodeId: 'elementid',
        },
        visibleAffectedTokenCount: 1,
        parentScopeIds: {},
      },
    });

    expect(modificationsStore.generateModificationsPayload()).toEqual([
      {
        modification: 'ADD_TOKEN',
        toFlowNodeId: 'flow_node_0',
        variables: undefined,
        ancestorElementInstanceKey: undefined,
      },
      {
        modification: 'ADD_TOKEN',
        toFlowNodeId: 'flow_node_1',
        variables: {
          'first-parent-scope': [
            {
              name10: 'value10',
            },
          ],
          flow_node_1: [{name1: 'value1', name2: 'value2'}],
        },
        ancestorElementInstanceKey: undefined,
      },
      {modification: 'CANCEL_TOKEN', fromFlowNodeId: 'flow_node_2'},
      {
        modification: 'MOVE_TOKEN',
        fromFlowNodeId: 'flow_node_3',
        toFlowNodeId: 'flow_node_4',
        newTokensCount: 1,
        variables: {
          flow_node_4: [{name6: 'value6'}],
          'first-parent': [
            {
              name7: 'value7',
              name8: 'value8',
            },
          ],
          'third-parent': [
            {
              name9: 'value9',
            },
          ],
        },
      },
      {
        fromFlowNodeInstanceKey: 'some_instance_key',
        modification: 'CANCEL_TOKEN',
      },
      {
        fromFlowNodeInstanceKey: 'some_instance_key_2',
        modification: 'MOVE_TOKEN',
        newTokensCount: 1,
        toFlowNodeId: 'flow_node_7',
      },
      {
        ancestorElementInstanceKey: 'some-ancestor-instance-key',
        modification: 'ADD_TOKEN',
        toFlowNodeId: 'flow_node_11',
        variables: undefined,
      },
      {
        modification: 'ADD_VARIABLE',
        scopeKey: 'random-scope-id-5',
        variables: {name3: 'value3'},
      },
      {
        modification: 'ADD_VARIABLE',
        scopeKey: 'random-scope-id-5',
        variables: {name4: 'value4'},
      },
      {
        modification: 'EDIT_VARIABLE',
        scopeKey: 'random-scope-id-5',
        variables: {name5: 'value5-edited2'},
      },
    ]);
  });

  it('should add tokens to flow nodes that has multiple running scopes', async () => {
    expect(modificationsStore.state.sourceFlowNodeIdForAddOperation).toBeNull();

    modificationsStore.startAddingToken('subprocess-service-task');
    expect(modificationsStore.state.sourceFlowNodeIdForAddOperation).toBe(
      'subprocess-service-task',
    );

    modificationsStore.finishAddingToken(
      {},
      'multi-instance-subprocess',
      'some-instance-key',
    );

    expect(
      modificationsStore.state.sourceFlowNodeIdForMoveOperation,
    ).toBeNull();
    expect(modificationsStore.state.status).toBe('enabled');
  });
});
