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
    expect(modificationsStore.state.modifications).toEqual([]);
    modificationsStore.addModification({
      type: 'token',
      modification: {
        operation: 'add',
        flowNode: {id: '1', name: 'flow-node-1'},
        affectedTokenCount: 1,
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(1);

    modificationsStore.addModification({
      type: 'token',
      modification: {
        operation: 'cancel',
        flowNode: {id: '2', name: 'flow-node-2'},
        affectedTokenCount: 3,
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.addModification({
      type: 'token',
      modification: {
        operation: 'move',
        flowNode: {id: '3', name: 'flow-node-3'},
        targetFlowNode: {id: '4', name: 'flow-node-4'},
        affectedTokenCount: 2,
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(3);

    modificationsStore.removeFlowNodeModification('non-existing-flow-node');
    expect(modificationsStore.state.modifications.length).toEqual(3);
    modificationsStore.removeFlowNodeModification('4');
    expect(modificationsStore.state.modifications.length).toEqual(3);
    modificationsStore.removeFlowNodeModification('2');
    expect(modificationsStore.state.modifications.length).toEqual(2);
  });

  it('should add/remove variable modifications', async () => {
    expect(modificationsStore.state.modifications).toEqual([]);
    modificationsStore.addModification({
      type: 'variable',
      modification: {
        operation: 'add',
        flowNode: {id: '1', name: 'flow-node-1'},
        name: 'variable1',
        oldValue: 'variable1-oldValue',
        newValue: 'variable1-newValue',
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(1);

    modificationsStore.addModification({
      type: 'variable',
      modification: {
        operation: 'edit',
        flowNode: {id: '2', name: 'flow-node-2'},
        name: 'variable1',
        oldValue: 'variable2-oldValue',
        newValue: 'variable2-newValue',
      },
    });

    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.removeVariableModification(
      'non-existing-variable',
      'variable1'
    );
    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.removeVariableModification(
      '1',
      'non-existing-variable-name'
    );
    expect(modificationsStore.state.modifications.length).toEqual(2);

    modificationsStore.removeVariableModification('1', 'variable1');
    expect(modificationsStore.state.modifications.length).toEqual(1);
    expect(modificationsStore.state.modifications).toEqual([
      {
        modification: {
          flowNode: {
            id: '2',
            name: 'flow-node-2',
          },
          name: 'variable1',
          newValue: 'variable2-newValue',
          oldValue: 'variable2-oldValue',
          operation: 'edit',
        },
        type: 'variable',
      },
    ]);
  });

  it('should remove last modification', async () => {
    modificationsStore.addModification({
      type: 'token',
      modification: {
        operation: 'add',
        flowNode: {id: '1', name: 'flow-node-1'},
        affectedTokenCount: 1,
      },
    });

    modificationsStore.addModification({
      type: 'token',
      modification: {
        operation: 'cancel',
        flowNode: {id: '2', name: 'flow-node-2'},
        affectedTokenCount: 3,
      },
    });

    expect(modificationsStore.lastModification).toEqual({
      modification: {
        operation: 'cancel',
        flowNode: {id: '2', name: 'flow-node-2'},
        affectedTokenCount: 3,
      },
      type: 'token',
    });

    modificationsStore.removeLastModification();

    expect(modificationsStore.lastModification).toEqual({
      type: 'token',
      modification: {
        operation: 'add',
        flowNode: {id: '1', name: 'flow-node-1'},
        affectedTokenCount: 1,
      },
    });

    modificationsStore.removeLastModification();

    expect(modificationsStore.lastModification).toEqual(undefined);
  });

  it('should get modifications by flow node', async () => {
    modificationsStore.addModification({
      type: 'token',
      modification: {
        operation: 'add',
        flowNode: {id: 'flowNode1', name: 'flow-node-1'},
        affectedTokenCount: 1,
      },
    });

    modificationsStore.addModification({
      type: 'token',
      modification: {
        operation: 'add',
        flowNode: {id: 'flowNode1', name: 'flow-node-1'},
        affectedTokenCount: 1,
      },
    });

    modificationsStore.addModification({
      type: 'token',
      modification: {
        operation: 'cancel',
        flowNode: {id: 'flowNode2', name: 'flow-node-2'},
        affectedTokenCount: 3,
      },
    });

    modificationsStore.addModification({
      type: 'token',
      modification: {
        operation: 'move',
        flowNode: {id: 'flowNode3', name: 'flow-node-3'},
        targetFlowNode: {id: 'flowNode4', name: 'flow-node-4'},
        affectedTokenCount: 3,
      },
    });

    modificationsStore.addModification({
      type: 'token',
      modification: {
        operation: 'add',
        flowNode: {id: 'flowNode5', name: 'flow-node-5'},
        affectedTokenCount: 1,
      },
    });

    modificationsStore.addModification({
      type: 'token',
      modification: {
        operation: 'move',
        flowNode: {id: 'flowNode5', name: 'flow-node-5'},
        targetFlowNode: {id: 'flowNode6', name: 'flow-node-6'},
        affectedTokenCount: 2,
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
      modification: {
        operation: 'add',
        flowNode: {id: 'flowNode1', name: 'flow-node-1'},
        affectedTokenCount: 1,
      },
    });

    expect(
      modificationsStore.isCancelModificationAppliedOnFlowNode('flowNode1')
    ).toBe(false);

    modificationsStore.addModification({
      type: 'token',
      modification: {
        operation: 'cancel',
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
      modification: {
        operation: 'move',
        flowNode: {id: 'flowNode2', name: 'flow-node-2'},
        targetFlowNode: {id: 'flowNode3', name: 'flow-node-3'},
        affectedTokenCount: 1,
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
});
