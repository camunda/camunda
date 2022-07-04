/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {modificationsStore} from './modifications';

describe('stores/modifications', () => {
  afterEach(() => {
    modificationsStore.reset();
  });

  it('should enable/disable modification mode', async () => {
    expect(modificationsStore.state.isModificationModeEnabled).toBe(false);
    modificationsStore.enableModificationMode();
    expect(modificationsStore.state.isModificationModeEnabled).toBe(true);
    modificationsStore.disableModificationMode();
    expect(modificationsStore.state.isModificationModeEnabled).toBe(false);
  });

  it('should add/remove flow node modifications', async () => {
    expect(modificationsStore.state.flowNodeModifications).toEqual([]);
    modificationsStore.addFlowNodeModification({
      operation: 'add',
      flowNode: {id: '1', name: 'flow-node-1'},
      affectedTokenCount: 1,
    });

    expect(modificationsStore.state.flowNodeModifications.length).toEqual(1);

    modificationsStore.addFlowNodeModification({
      operation: 'cancel',
      flowNode: {id: '2', name: 'flow-node-2'},
      affectedTokenCount: 3,
    });

    expect(modificationsStore.state.flowNodeModifications.length).toEqual(2);

    modificationsStore.addFlowNodeModification({
      operation: 'move',
      flowNode: {id: '3', name: 'flow-node-3'},
      targetFlowNode: {id: '4', name: 'flow-node-4'},
      affectedTokenCount: 2,
    });

    expect(modificationsStore.state.flowNodeModifications.length).toEqual(3);

    modificationsStore.removeFlowNodeModification('non-existing-flow-node');
    expect(modificationsStore.state.flowNodeModifications.length).toEqual(3);
    modificationsStore.removeFlowNodeModification('4');
    expect(modificationsStore.state.flowNodeModifications.length).toEqual(3);
    modificationsStore.removeFlowNodeModification('2');
    expect(modificationsStore.state.flowNodeModifications.length).toEqual(2);
  });

  it('should add/remove variable modifications', async () => {
    expect(modificationsStore.state.variableModifications).toEqual([]);
    modificationsStore.addVariableModification({
      operation: 'add',
      flowNode: {id: '1', name: 'flow-node-1'},
      name: 'variable1',
      oldValue: 'variable1-oldValue',
      newValue: 'variable1-newValue',
    });

    expect(modificationsStore.state.variableModifications.length).toEqual(1);

    modificationsStore.addVariableModification({
      operation: 'edit',
      flowNode: {id: '2', name: 'flow-node-2'},
      name: 'variable1',
      oldValue: 'variable2-oldValue',
      newValue: 'variable2-newValue',
    });
    expect(modificationsStore.state.variableModifications.length).toEqual(2);

    modificationsStore.removeVariableModification(
      'non-existing-variable',
      'variable1'
    );
    expect(modificationsStore.state.variableModifications.length).toEqual(2);

    modificationsStore.removeVariableModification(
      '1',
      'non-existing-variable-name'
    );
    expect(modificationsStore.state.variableModifications.length).toEqual(2);

    modificationsStore.removeVariableModification('1', 'variable1');
    expect(modificationsStore.state.variableModifications.length).toEqual(1);
  });
});
