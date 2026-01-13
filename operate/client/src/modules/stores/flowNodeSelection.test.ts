/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstanceDetailsStore} from './processInstanceDetails';
import {flowNodeSelectionStore} from './flowNodeSelection';
import {modificationsStore} from './modifications';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {createInstance} from 'modules/testUtils';

const PROCESS_INSTANCE_ID = '2251799813689404';

describe('stores/flowNodeSelection', () => {
  beforeAll(async () => {
    mockFetchProcessInstance().withSuccess(
      createInstance({
        id: PROCESS_INSTANCE_ID,
        state: 'ACTIVE',
        processName: 'some process name',
      }),
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
      selectionForNewAddedTokenScope,
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
      selectionForNewAddedTokenParentScope,
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
      selectionForNewMovedTokenFirstScope,
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
      selectionForNewMovedTokenSecondScope,
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
      selectionForNewMovedTokenParentScope,
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
});
