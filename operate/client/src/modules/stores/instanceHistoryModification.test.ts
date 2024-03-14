/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
        'process_1',
      ),
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
        'subprocess_1',
      ),
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
      startEventModification.payload,
    );

    modificationsStore.removeFlowNodeModification(
      endEvent2Modification.payload,
    );

    expect(
      instanceHistoryModificationStore.getVisibleChildPlaceholders(
        'id',
        'process_1',
      ),
    ).toEqual([]);

    expect(
      instanceHistoryModificationStore.getVisibleChildPlaceholders(
        'id',
        'subprocess_1',
      ),
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
        'process_1',
      ),
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
        'process_1',
      ),
    ).toEqual([]);
  });

  it('should not add multiple modifications when source is multi instance', () => {
    modificationsStore.addModification(subprocessModification);

    expect(
      instanceHistoryModificationStore.getVisibleChildPlaceholders(
        'id',
        'subprocess_1',
      ),
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
