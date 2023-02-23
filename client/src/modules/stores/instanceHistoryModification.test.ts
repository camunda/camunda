/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {instanceHistoryModificationStore} from './instanceHistoryModification';
import {FlowNodeModification, modificationsStore} from './modifications';

const businessObjects: {
  [flowNodeId: string]: {id: string; $parent: {id: string}; $type: string};
} = {
  startEvent_1: {
    id: 'startEvent_1',
    $parent: {id: 'process_1'},
    $type: 'START_EVENT',
  },
  endEvent_1: {
    id: 'endEvent_1',
    $parent: {id: 'subprocess_1'},
    $type: 'END_EVENT',
  },
  endEvent_2: {
    id: 'endEvent_2',
    $parent: {id: 'subprocess_1'},
    $type: 'END_EVENT',
  },
  subprocess_1: {
    id: 'subprocess_1',
    $parent: {id: 'process_1'},
    $type: 'SUB_PROCESS',
  },
};

jest.mock('modules/stores/processInstanceDetailsDiagram', () => ({
  processInstanceDetailsDiagramStore: {
    businessObjects,
  },
}));

const startEventModification: FlowNodeModification = {
  type: 'token',
  payload: {
    operation: 'ADD_TOKEN',
    scopeId: generateUniqueID(),
    flowNode: {id: 'startEvent_1', name: 'Start Event 1'},
    affectedTokenCount: 1,
    visibleAffectedTokenCount: 1,
    parentScopeIds: {},
  },
};

const endEvent1Modification: FlowNodeModification = {
  type: 'token',
  payload: {
    operation: 'MOVE_TOKEN',
    targetFlowNode: {id: 'endEvent_1', name: 'End Event 1'},
    flowNode: {id: 'startEvent_1', name: 'Start Event 1'},
    affectedTokenCount: 2,
    visibleAffectedTokenCount: 2,
    scopeIds: [generateUniqueID(), generateUniqueID()],
    parentScopeIds: {},
  },
};

const endEvent2Modification: FlowNodeModification = {
  type: 'token',
  payload: {
    operation: 'CANCEL_TOKEN',
    flowNode: {id: 'endEvent_2', name: 'End Event 2'},
    affectedTokenCount: 1,
    visibleAffectedTokenCount: 1,
  },
};

const subprocessModification: FlowNodeModification = {
  type: 'token',
  payload: {
    operation: 'MOVE_TOKEN',
    targetFlowNode: {id: 'endEvent_1', name: 'End Event 1'},
    flowNode: {id: 'subprocess_1', name: 'Sub Process 1'},
    affectedTokenCount: 5,
    visibleAffectedTokenCount: 5,
    scopeIds: [generateUniqueID()],
    parentScopeIds: {},
  },
};

describe('stores/instanceHistoryModification', () => {
  beforeEach(() => {
    modificationsStore.enableModificationMode();
  });

  afterEach(() => {
    modificationsStore.reset();
    instanceHistoryModificationStore.reset();
  });

  it('should add and remove multiple flownodes', () => {
    modificationsStore.addModification(startEventModification);
    modificationsStore.addModification(endEvent1Modification);
    modificationsStore.addModification(endEvent2Modification);

    expect(
      instanceHistoryModificationStore.getVisibleChildPlaceholders(
        'id',
        'process_1'
      )
    ).toEqual([
      {
        flowNodeId: 'startEvent_1',
        type: 'START_EVENT',
        id: expect.any(String),
        endDate: null,
        sortValues: [],
        startDate: '',
        treePath: '',
        isPlaceholder: true,
      },
    ]);

    expect(
      instanceHistoryModificationStore.getVisibleChildPlaceholders(
        'id',
        'subprocess_1'
      )
    ).toEqual([
      {
        flowNodeId: 'endEvent_1',
        type: 'END_EVENT',
        id: expect.any(String),
        endDate: null,
        sortValues: [],
        startDate: '',
        treePath: '',
        isPlaceholder: true,
      },
      {
        flowNodeId: 'endEvent_1',
        type: 'END_EVENT',
        id: expect.any(String),
        endDate: null,
        sortValues: [],
        startDate: '',
        treePath: '',
        isPlaceholder: true,
      },
    ]);

    modificationsStore.removeFlowNodeModification(
      startEventModification.payload
    );

    modificationsStore.removeFlowNodeModification(
      endEvent2Modification.payload
    );

    expect(
      instanceHistoryModificationStore.getVisibleChildPlaceholders(
        'id',
        'process_1'
      )
    ).toEqual([]);

    expect(
      instanceHistoryModificationStore.getVisibleChildPlaceholders(
        'id',
        'subprocess_1'
      )
    ).toEqual([
      {
        flowNodeId: 'endEvent_1',
        type: 'END_EVENT',
        id: expect.any(String),
        endDate: null,
        sortValues: [],
        startDate: '',
        treePath: '',
        isPlaceholder: true,
      },
      {
        flowNodeId: 'endEvent_1',
        type: 'END_EVENT',
        id: expect.any(String),
        endDate: null,
        sortValues: [],
        startDate: '',
        treePath: '',
        isPlaceholder: true,
      },
    ]);
  });

  it('should reset when modification mode is disabled', async () => {
    modificationsStore.addModification(startEventModification);

    expect(
      instanceHistoryModificationStore.getVisibleChildPlaceholders(
        'id',
        'process_1'
      )
    ).toEqual([
      {
        flowNodeId: 'startEvent_1',
        type: 'START_EVENT',
        id: expect.any(String),
        endDate: null,
        sortValues: [],
        startDate: '',
        treePath: '',
        isPlaceholder: true,
      },
    ]);

    modificationsStore.reset();

    expect(
      instanceHistoryModificationStore.getVisibleChildPlaceholders(
        'id',
        'process_1'
      )
    ).toEqual([]);
  });

  it('should not add multiple modifications when source is multi instance', () => {
    modificationsStore.addModification(subprocessModification);

    expect(
      instanceHistoryModificationStore.getVisibleChildPlaceholders(
        'id',
        'subprocess_1'
      )
    ).toEqual([
      {
        flowNodeId: 'endEvent_1',
        type: 'END_EVENT',
        id: expect.any(String),
        endDate: null,
        sortValues: [],
        startDate: '',
        treePath: '',
        isPlaceholder: true,
      },
    ]);
  });
});
