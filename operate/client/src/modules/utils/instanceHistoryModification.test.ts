/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {
  FlowNodeModification,
  modificationsStore,
} from 'modules/stores//modifications';
import {getVisibleChildPlaceholders} from 'modules/utils/instanceHistoryModification';
import {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';

const businessObjects: BusinessObjects = {
  startEvent_1: {
    id: 'startEvent_1',
    name: '',
    $parent: {id: 'process_1', name: '', $type: 'bpmn:Process'},
    $type: 'bpmn:StartEvent',
  },
  endEvent_1: {
    id: 'endEvent_1',
    name: '',
    $parent: {id: 'subprocess_1', name: '', $type: 'bpmn:SubProcess'},
    $type: 'bpmn:EndEvent',
  },
  endEvent_2: {
    id: 'endEvent_2',
    name: '',
    $parent: {id: 'subprocess_1', name: '', $type: 'bpmn:SubProcess'},
    $type: 'bpmn:EndEvent',
  },
  subprocess_1: {
    id: 'subprocess_1',
    name: '',
    $parent: {id: 'process_1', name: '', $type: 'bpmn:Process'},
    $type: 'bpmn:SubProcess',
  },
};

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
      getVisibleChildPlaceholders('id', 'process_1', businessObjects),
    ).toEqual([
      {
        flowNodeId: 'startEvent_1',
        type: 'bpmn:StartEvent',
        id: expect.any(String),
        endDate: null,
        sortValues: [],
        startDate: '',
        treePath: '',
        isPlaceholder: true,
      },
    ]);

    expect(
      getVisibleChildPlaceholders('id', 'subprocess_1', businessObjects),
    ).toEqual([
      {
        flowNodeId: 'endEvent_1',
        type: 'bpmn:EndEvent',
        id: expect.any(String),
        endDate: null,
        sortValues: [],
        startDate: '',
        treePath: '',
        isPlaceholder: true,
      },
      {
        flowNodeId: 'endEvent_1',
        type: 'bpmn:EndEvent',
        id: expect.any(String),
        endDate: null,
        sortValues: [],
        startDate: '',
        treePath: '',
        isPlaceholder: true,
      },
    ]);

    modificationsStore.removeFlowNodeModification(
      startEventModification.payload,
    );

    modificationsStore.removeFlowNodeModification(
      endEvent2Modification.payload,
    );

    expect(
      getVisibleChildPlaceholders('id', 'process_1', businessObjects),
    ).toEqual([]);

    expect(
      getVisibleChildPlaceholders('id', 'subprocess_1', businessObjects),
    ).toEqual([
      {
        flowNodeId: 'endEvent_1',
        type: 'bpmn:EndEvent',
        id: expect.any(String),
        endDate: null,
        sortValues: [],
        startDate: '',
        treePath: '',
        isPlaceholder: true,
      },
      {
        flowNodeId: 'endEvent_1',
        type: 'bpmn:EndEvent',
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
      getVisibleChildPlaceholders('id', 'process_1', businessObjects),
    ).toEqual([
      {
        flowNodeId: 'startEvent_1',
        type: 'bpmn:StartEvent',
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
      getVisibleChildPlaceholders('id', 'process_1', businessObjects),
    ).toEqual([]);
  });

  it('should not add multiple modifications when source is multi instance', () => {
    modificationsStore.addModification(subprocessModification);

    expect(
      getVisibleChildPlaceholders('id', 'subprocess_1', businessObjects),
    ).toEqual([
      {
        flowNodeId: 'endEvent_1',
        type: 'bpmn:EndEvent',
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
