/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook} from '@testing-library/react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  useBatchOperationMutationRequestBody,
  useCancelProcessInstancesBatchOperationMutationRequestBody,
  useDeleteProcessInstancesBatchOperationMutationRequestBody,
  useResumeProcessInstancesBatchOperationMutationRequestBody,
  useSuspendProcessInstancesBatchOperationMutationRequestBody,
} from './useBatchOperationMutationRequestBody';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {processInstancesSelectionStore} from 'modules/stores/instancesSelection';

const getWrapper = (initialSearchParams?: Record<string, string>) => {
  const Wrapper = ({children}: {children: React.ReactNode}) => {
    const searchParams = new URLSearchParams(initialSearchParams);

    return (
      <MemoryRouter initialEntries={[`/processes?${searchParams.toString()}`]}>
        <Routes>
          <Route path="/processes" element={children} />
        </Routes>
      </MemoryRouter>
    );
  };
  return Wrapper;
};

const processOperationHooks = [
  {
    name: 'retry and batch modification',
    useRequestBody: useBatchOperationMutationRequestBody,
    eligibleIdsKey: 'visibleRunningIds',
  },
  {
    name: 'cancel',
    useRequestBody: useCancelProcessInstancesBatchOperationMutationRequestBody,
    eligibleIdsKey: 'visibleSuspendedIds',
  },
  {
    name: 'suspend',
    useRequestBody: useSuspendProcessInstancesBatchOperationMutationRequestBody,
    eligibleIdsKey: 'visibleRunningIds',
  },
  {
    name: 'resume',
    useRequestBody: useResumeProcessInstancesBatchOperationMutationRequestBody,
    eligibleIdsKey: 'visibleSuspendedIds',
  },
  {
    name: 'delete',
    useRequestBody: useDeleteProcessInstancesBatchOperationMutationRequestBody,
    eligibleIdsKey: 'visibleFinishedIds',
  },
] as const;

describe('useBatchOperationMutationRequestBody', () => {
  afterEach(() => {
    variableFilterStore.reset();
    processInstancesSelectionStore.resetState();
  });

  it('should return basic request body with no filters', () => {
    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toEqual({
      filter: {},
    });
  });

  it('should include search params in filter', () => {
    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper({
        active: 'true',
        incidents: 'true',
      }),
    });

    expect(result.current).toEqual({
      filter: {
        $or: [{state: {$in: ['ACTIVE']}}, {hasIncident: true}],
      },
    });
  });

  it('should include suspended instances in the cancel filter alongside active and incident instances', () => {
    const {result} = renderHook(
      () => useCancelProcessInstancesBatchOperationMutationRequestBody(),
      {
        wrapper: getWrapper({
          active: 'true',
          suspended: 'true',
          incidents: 'true',
        }),
      },
    );

    expect(result.current).toEqual({
      filter: {
        $or: [
          {state: {$eq: 'ACTIVE'}, hasIncident: false},
          {state: {$eq: 'SUSPENDED'}},
          {hasIncident: true, state: {$neq: 'SUSPENDED'}},
        ],
      },
    });
  });

  it('should leave suspended instances out of retry filters', () => {
    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper({
        active: 'true',
        suspended: 'true',
        incidents: 'true',
      }),
    });

    expect(result.current).toEqual({
      filter: {
        $or: [{state: {$in: ['ACTIVE']}}, {hasIncident: true}],
      },
    });
  });

  it('should include selected active and suspended keys but exclude finished keys for cancel', () => {
    processInstancesSelectionStore.state.selectionMode = 'INCLUDE';
    processInstancesSelectionStore.state.selectedIds = ['1', '2', '3'];
    processInstancesSelectionStore.setRuntime({
      totalCount: 4,
      visibleIds: ['1', '2', '3', '4'],
      visibleRunningIds: ['1'],
      visibleSuspendedIds: ['2'],
      visibleFinishedIds: ['3'],
    });

    const {result} = renderHook(
      () => useCancelProcessInstancesBatchOperationMutationRequestBody(),
      {
        wrapper: getWrapper({active: 'true', suspended: 'true'}),
      },
    );

    expect(result.current).toEqual({
      filter: {
        $or: [
          {state: {$eq: 'ACTIVE'}, hasIncident: false},
          {state: {$eq: 'SUSPENDED'}},
        ],
        processInstanceKey: {$in: ['1', '2']},
      },
    });
  });

  it.each(processOperationHooks)(
    'should retain selected process instance keys for $name when eligible instances disappear',
    ({useRequestBody, eligibleIdsKey}) => {
      processInstancesSelectionStore.state.selectionMode = 'INCLUDE';
      processInstancesSelectionStore.state.selectedIds = ['123'];
      processInstancesSelectionStore.setRuntime({
        totalCount: 2,
        visibleIds: ['123'],
        [eligibleIdsKey]: ['123'],
      });

      const {result, rerender} = renderHook(() => useRequestBody(), {
        wrapper: getWrapper(),
      });

      expect(result.current).toEqual({
        filter: {
          processInstanceKey: {$in: ['123']},
        },
      });

      processInstancesSelectionStore.setRuntime({
        totalCount: 2,
        visibleIds: [],
        [eligibleIdsKey]: [],
      });
      rerender();

      expect(result.current).toEqual({
        filter: {
          processInstanceKey: {$in: ['123']},
        },
      });
    },
  );

  it('should not include process instance keys when selectedIds is empty', () => {
    processInstancesSelectionStore.state.selectionMode = 'INCLUDE';
    processInstancesSelectionStore.state.selectedIds = [];

    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toEqual({
      filter: {},
    });
  });

  it('should include excluded process instance keys when in EXCLUDE mode', () => {
    processInstancesSelectionStore.state.selectionMode = 'EXCLUDE';
    processInstancesSelectionStore.state.selectedIds = ['111', '222'];

    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toEqual({
      filter: {
        processInstanceKey: {$notIn: ['111', '222']},
      },
    });
  });

  it('should not include excludeIds when in ALL mode', () => {
    processInstancesSelectionStore.state.selectionMode = 'ALL';
    processInstancesSelectionStore.state.selectedIds = [];

    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toEqual({
      filter: {},
    });
  });

  it('should include variable conditions', () => {
    variableFilterStore.setConditions([
      {name: 'testVar', operator: 'equals', value: '"value1"'},
    ]);

    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toEqual({
      filter: {
        variables: [
          {
            name: 'testVar',
            value: {$eq: '"value1"'},
          },
        ],
      },
    });
  });

  it('should include multiple variable conditions', () => {
    variableFilterStore.setConditions([
      {name: 'testVar', operator: 'equals', value: '"value1"'},
      {name: 'status', operator: 'notEqual', value: '"draft"'},
    ]);

    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toEqual({
      filter: {
        variables: [
          {name: 'testVar', value: {$eq: '"value1"'}},
          {name: 'status', value: {$neq: '"draft"'}},
        ],
      },
    });
  });

  it('should combine search params, selected keys, and variable conditions', () => {
    processInstancesSelectionStore.state.selectionMode = 'INCLUDE';
    processInstancesSelectionStore.state.selectedIds = ['123', '456'];
    processInstancesSelectionStore.setRuntime({
      totalCount: 3,
      visibleIds: ['123', '456'],
      visibleRunningIds: ['123', '456'],
    });

    variableFilterStore.setConditions([
      {name: 'status', operator: 'equals', value: '"active"'},
    ]);

    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper({
        active: 'true',
      }),
    });

    expect(result.current).toEqual({
      filter: {
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
        processInstanceKey: {$in: ['123', '456']},
        variables: [
          {
            name: 'status',
            value: {$eq: '"active"'},
          },
        ],
      },
    });
  });

  it('should not include variables when no conditions set', () => {
    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toEqual({
      filter: {},
    });
  });

  it('should handle complex search params with tenant filter', () => {
    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper({
        tenantId: '<default>',
        active: 'true',
      }),
    });

    expect(result.current).toEqual({
      filter: {
        tenantId: {$eq: '<default>'},
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });
  });

  it('should handle multiple state filters', () => {
    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper({
        active: 'true',
        completed: 'true',
        canceled: 'true',
        incident: 'true',
      }),
    });

    expect(result.current).toEqual({
      filter: {
        state: {$in: ['ACTIVE', 'COMPLETED', 'TERMINATED']},
        hasIncident: false,
      },
    });
  });

  it('should handle parent instance ID filter', () => {
    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper({
        parentProcessInstanceKey: '12345',
        active: 'true',
      }),
    });

    expect(result.current).toEqual({
      filter: {
        parentProcessInstanceKey: {$eq: '12345'},
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });
  });

  it('should handle process definition with version filter', () => {
    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper({
        processDefinitionId: 'order-process',
        processDefinitionVersion: '2',
        active: 'true',
      }),
    });

    expect(result.current).toEqual({
      filter: {
        hasIncident: false,
        processDefinitionId: {$eq: 'order-process'},
        processDefinitionVersion: 2,
        state: {$eq: 'ACTIVE'},
      },
    });
  });

  it('should ignore all version filter', () => {
    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper({
        processDefinitionId: 'order-process',
        processDefinitionVersion: 'all',
        active: 'true',
      }),
    });

    expect(result.current).toEqual({
      filter: {
        hasIncident: false,
        processDefinitionId: {$eq: 'order-process'},
        state: {$eq: 'ACTIVE'},
      },
    });
  });

  it('should handle elementId filter', () => {
    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper({
        elementId: 'task-1',
        active: 'true',
      }),
    });

    expect(result.current).toEqual({
      filter: {
        elementId: {$eq: 'task-1'},
        elementInstanceState: {$eq: 'ACTIVE'},
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });
  });

  it('should handle batchOperationKey filter', () => {
    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper({
        batchOperationKey: 'op-123',
        active: 'true',
      }),
    });

    expect(result.current).toEqual({
      filter: {
        batchOperationKey: {$eq: 'op-123'},
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });
  });
});
