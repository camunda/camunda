/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {modificationsStore} from './modifications';
import {mockServer} from 'modules/mock-server/node';
import {rest} from 'msw';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {mockProcessForModifications} from 'modules/mocks/mockProcessForModifications';
import {flowNodeStatesStore} from './flowNodeStates';
import {generateUniqueID} from 'modules/utils/generateUniqueID';

describe('stores/modifications', () => {
  afterEach(() => {
    modificationsStore.reset();
    processInstanceDetailsDiagramStore.reset();
    flowNodeStatesStore.reset();
  });

  it('should enable/disable modification mode', async () => {
    expect(modificationsStore.isModificationModeEnabled).toBe(false);
    modificationsStore.enableModificationMode();
    expect(modificationsStore.isModificationModeEnabled).toBe(true);
    modificationsStore.disableModificationMode();
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
        flowNode: {id: '1', name: 'flow-node-1'},
        affectedTokenCount: 1,
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(1);
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: '2', name: 'flow-node-2'},
        affectedTokenCount: 3,
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: '3', name: 'flow-node-3'},
        targetFlowNode: {id: '4', name: 'flow-node-4'},
        affectedTokenCount: 2,
        scopeIds: [uniqueIDForMove],
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(3);

    expect(modificationsStore.flowNodeModifications).toEqual([
      {
        operation: 'ADD_TOKEN',
        scopeId: uniqueID,
        flowNode: {id: '1', name: 'flow-node-1'},
        affectedTokenCount: 1,
      },
      {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: '2', name: 'flow-node-2'},
        affectedTokenCount: 3,
      },
      {
        operation: 'MOVE_TOKEN',
        flowNode: {id: '3', name: 'flow-node-3'},
        targetFlowNode: {id: '4', name: 'flow-node-4'},
        affectedTokenCount: 2,
        scopeIds: [uniqueIDForMove],
      },
    ]);

    modificationsStore.removeFlowNodeModification({
      flowNode: {id: 'non-existing-flow-node', name: ''},
      operation: 'ADD_TOKEN',
      scopeId: '1',
      affectedTokenCount: 1,
    });
    expect(modificationsStore.state.modifications.length).toEqual(3);
    modificationsStore.removeFlowNodeModification({
      flowNode: {id: '4', name: ''},
      operation: 'ADD_TOKEN',
      scopeId: '1',
      affectedTokenCount: 1,
    });
    expect(modificationsStore.state.modifications.length).toEqual(3);
    modificationsStore.removeFlowNodeModification({
      flowNode: {id: '2', name: ''},
      operation: 'ADD_TOKEN',
      scopeId: '2',
      affectedTokenCount: 1,
    });
    expect(modificationsStore.state.modifications.length).toEqual(3);
    modificationsStore.removeFlowNodeModification({
      flowNode: {id: '2', name: ''},
      operation: 'CANCEL_TOKEN',
      affectedTokenCount: 1,
    });
    expect(modificationsStore.state.modifications.length).toEqual(2);
    modificationsStore.removeFlowNodeModification({
      flowNode: {id: '1', name: ''},
      operation: 'ADD_TOKEN',
      affectedTokenCount: 1,
      scopeId: uniqueID,
    });
    expect(modificationsStore.state.modifications.length).toEqual(1);

    expect(modificationsStore.flowNodeModifications).toEqual([
      {
        operation: 'MOVE_TOKEN',
        flowNode: {id: '3', name: 'flow-node-3'},
        targetFlowNode: {id: '4', name: 'flow-node-4'},
        affectedTokenCount: 2,
        scopeIds: [uniqueIDForMove],
      },
    ]);
  });

  it('should add/remove variable modifications', async () => {
    expect(modificationsStore.state.modifications).toEqual([]);
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        id: '1',
        scopeId: '1',
        flowNodeName: 'flow-node-1',
        name: 'variable1',
        oldValue: 'variable1-oldValue',
        newValue: 'variable1-newValue',
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(1);

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        id: '2',
        operation: 'EDIT_VARIABLE',
        scopeId: '2',
        flowNodeName: 'flow-node-2',
        name: 'variable1',
        oldValue: 'variable2-oldValue',
        newValue: 'variable2-newValue',
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.removeVariableModification(
      'non-existing-variable',
      'variable1',
      'EDIT_VARIABLE'
    );
    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.removeVariableModification(
      '1',
      'non-existing-variable-name',
      'ADD_VARIABLE'
    );
    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.removeVariableModification('1', '1', 'ADD_VARIABLE');
    expect(modificationsStore.state.modifications.length).toEqual(1);
    expect(modificationsStore.state.modifications).toEqual([
      {
        payload: {
          flowNodeName: 'flow-node-2',
          scopeId: '2',
          id: '2',
          name: 'variable1',
          newValue: 'variable2-newValue',
          oldValue: 'variable2-oldValue',
          operation: 'EDIT_VARIABLE',
        },
        type: 'variable',
      },
    ]);
  });

  it('should remove last modification', async () => {
    const uniqueID = generateUniqueID();

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: uniqueID,
        flowNode: {id: '1', name: 'flow-node-1'},
        affectedTokenCount: 1,
      },
    });

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: '2', name: 'flow-node-2'},
        affectedTokenCount: 3,
      },
    });

    expect(modificationsStore.lastModification).toEqual({
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: '2', name: 'flow-node-2'},
        affectedTokenCount: 3,
      },
      type: 'token',
    });

    modificationsStore.removeLastModification();

    expect(modificationsStore.lastModification).toEqual({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        flowNode: {id: '1', name: 'flow-node-1'},
        affectedTokenCount: 1,
        scopeId: uniqueID,
      },
    });

    modificationsStore.removeLastModification();

    expect(modificationsStore.lastModification).toEqual(undefined);
  });

  it('should get modifications by flow node', async () => {
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: generateUniqueID(),
        flowNode: {id: 'flowNode1', name: 'flow-node-1'},
        affectedTokenCount: 1,
      },
    });

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: generateUniqueID(),
        flowNode: {id: 'flowNode1', name: 'flow-node-1'},
        affectedTokenCount: 1,
      },
    });

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: 'flowNode2', name: 'flow-node-2'},
        affectedTokenCount: 3,
      },
    });

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: 'flowNode3', name: 'flow-node-3'},
        targetFlowNode: {id: 'flowNode4', name: 'flow-node-4'},
        affectedTokenCount: 3,
        scopeIds: ['1', '2', '3'],
      },
    });

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: generateUniqueID(),
        flowNode: {id: 'flowNode5', name: 'flow-node-5'},
        affectedTokenCount: 1,
      },
    });

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: 'flowNode5', name: 'flow-node-5'},
        targetFlowNode: {id: 'flowNode6', name: 'flow-node-6'},
        affectedTokenCount: 2,
        scopeIds: ['4', '5'],
      },
    });

    expect(modificationsStore.modificationsByFlowNode).toEqual({
      flowNode1: {
        cancelledTokens: 0,
        newTokens: 2,
      },
      flowNode2: {
        cancelledTokens: 3,
        newTokens: 0,
      },
      flowNode3: {
        cancelledTokens: 3,
        newTokens: 0,
      },
      flowNode4: {
        cancelledTokens: 0,
        newTokens: 3,
      },
      flowNode5: {
        cancelledTokens: 2,
        newTokens: 1,
      },
      flowNode6: {
        cancelledTokens: 0,
        newTokens: 2,
      },
    });
  });

  it('should check if tokens on a flow node is cancelled', async () => {
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        scopeId: generateUniqueID(),
        flowNode: {id: 'flowNode1', name: 'flow-node-1'},
        affectedTokenCount: 1,
      },
    });

    expect(
      modificationsStore.isCancelModificationAppliedOnFlowNode('flowNode1')
    ).toBe(false);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'CANCEL_TOKEN',
        flowNode: {id: 'flowNode1', name: 'flow-node-1'},
        affectedTokenCount: 1,
      },
    });
    expect(
      modificationsStore.isCancelModificationAppliedOnFlowNode('flowNode1')
    ).toBe(true);

    expect(
      modificationsStore.isCancelModificationAppliedOnFlowNode(
        'non-existing-flownode'
      )
    ).toBe(false);

    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'MOVE_TOKEN',
        flowNode: {id: 'flowNode2', name: 'flow-node-2'},
        targetFlowNode: {id: 'flowNode3', name: 'flow-node-3'},
        affectedTokenCount: 1,
        scopeIds: ['1'],
      },
    });
    expect(
      modificationsStore.isCancelModificationAppliedOnFlowNode('flowNode2')
    ).toBe(true);
    expect(
      modificationsStore.isCancelModificationAppliedOnFlowNode('flowNode3')
    ).toBe(false);
  });

  it('should move tokens', async () => {
    expect(modificationsStore.modificationsByFlowNode).toEqual({});
    expect(
      modificationsStore.state.sourceFlowNodeIdForMoveOperation
    ).toBeNull();

    modificationsStore.startMovingToken('flowNode1');
    expect(modificationsStore.state.sourceFlowNodeIdForMoveOperation).toBe(
      'flowNode1'
    );

    modificationsStore.finishMovingToken('flowNode2');

    expect(modificationsStore.modificationsByFlowNode).toEqual({
      flowNode1: {
        cancelledTokens: 2,
        newTokens: 0,
      },
      flowNode2: {
        cancelledTokens: 0,
        newTokens: 2,
      },
    });

    expect(
      modificationsStore.state.sourceFlowNodeIdForMoveOperation
    ).toBeNull();
  });

  it('should move tokens from multi instance process', async () => {
    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockProcessForModifications))
      ),
      rest.get(
        '/api/process-instances/:processId/flow-node-states',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              StartEvent_1: 'COMPLETED',
              'service-task-1': 'COMPLETED',
              'multi-instance-subprocess': 'INCIDENT',
              'subprocess-start-1': 'COMPLETED',
              'subprocess-service-task': 'INCIDENT',
              'service-task-7': 'ACTIVE',
              'message-boundary': 'ACTIVE',
            })
          )
      )
    );
    await processInstanceDetailsDiagramStore.fetchProcessXml(
      'processInstanceId'
    );
    await flowNodeStatesStore.fetchFlowNodeStates('processInstanceId');
    modificationsStore.startMovingToken('multi-instance-subprocess');
    modificationsStore.finishMovingToken('service-task-7');

    expect(modificationsStore.modificationsByFlowNode).toEqual({
      'multi-instance-subprocess': {
        cancelledTokens: 2,
        newTokens: 0,
      },
      'service-task-7': {
        cancelledTokens: 0,
        newTokens: 1,
      },
    });
  });

  it('should retrieve variable modifications correctly', () => {
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: '1',
        name: 'name1',
        newValue: 'value1',
      },
    });
    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: '1',
        name: 'name1',
        newValue: 'value2',
      },
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

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: '1',
        name: 'name2',
        newValue: 'value3',
      },
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

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'EDIT_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: 'existing-variable',
        name: 'existing-variable',
        newValue: '123',
      },
    });

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'EDIT_VARIABLE',
        scopeId: 'flow-node-1',
        flowNodeName: 'flowNode1',
        id: 'existing-variable',
        name: 'existing-variable',
        newValue: '1234',
      },
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
      },
    ]);

    modificationsStore.addModification({
      type: 'variable',
      payload: {
        operation: 'ADD_VARIABLE',
        scopeId: 'flow-node-2',
        flowNodeName: 'flowNode2',
        id: '1',
        name: 'name2',
        newValue: 'value3',
      },
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
});
