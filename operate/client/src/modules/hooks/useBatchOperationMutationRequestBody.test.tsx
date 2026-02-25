/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook} from '@testing-library/react';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {useBatchOperationMutationRequestBody} from './useBatchOperationMutationRequestBody';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';

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

  it('should not include process instance keys when checkedRunningProcessInstanceIds is empty', () => {
    processInstancesSelectionStore.state.selectionMode = 'INCLUDE';
    processInstancesSelectionStore.state.selectedProcessInstanceIds = [
      '123',
      '456',
      '789',
    ];

    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toEqual({
      filter: {},
    });
  });

  it('should not include process instance keys when selectedProcessInstanceIds is empty', () => {
    processInstancesSelectionStore.state.selectionMode = 'INCLUDE';
    processInstancesSelectionStore.state.selectedProcessInstanceIds = [];

    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toEqual({
      filter: {},
    });
  });

  it('should include excluded process instance keys when in EXCLUDE mode', () => {
    processInstancesSelectionStore.state.selectionMode = 'EXCLUDE';
    processInstancesSelectionStore.state.selectedProcessInstanceIds = [
      '111',
      '222',
    ];

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
    processInstancesSelectionStore.state.selectedProcessInstanceIds = [];

    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toEqual({
      filter: {},
    });
  });

  it('should include variable filter', () => {
    variableFilterStore.setVariable({
      name: 'testVar',
      values: '"value1"',
    });

    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toEqual({
      filter: {
        variables: [
          {
            name: 'testVar',
            value: '"value1"',
          },
        ],
      },
    });
  });

  it('should include multiple variable values', () => {
    variableFilterStore.setVariable({
      name: 'testVar',
      values: '"value1", "value2", "value3"',
    });

    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toEqual({
      filter: {
        variables: [
          {
            name: 'testVar',
            value: {$in: ['"value1"', '"value2"', '"value3"']},
          },
        ],
      },
    });
  });

  it('should combine search params, selected keys, and variable filter', () => {
    processInstancesSelectionStore.state.selectionMode = 'INCLUDE';
    processInstancesSelectionStore.state.selectedProcessInstanceIds = [
      '123',
      '456',
    ];

    variableFilterStore.setVariable({
      name: 'status',
      values: '"active"',
    });

    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper({
        active: 'true',
      }),
    });

    expect(result.current).toEqual({
      filter: {
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
        variables: [
          {
            name: 'status',
            value: '"active"',
          },
        ],
      },
    });
  });

  it('should not include variable filter when variable name is missing', () => {
    variableFilterStore.setVariable({
      name: '',
      values: '"value1"',
    });

    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toEqual({
      filter: {},
    });
  });

  it('should not include variable filter when variable values are missing', () => {
    variableFilterStore.setVariable({
      name: 'testVar',
      values: '',
    });

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
        tenant: '<default>',
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
        parentInstanceId: '12345',
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
        process: 'order-process',
        version: '2',
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
        process: 'order-process',
        version: 'all',
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
        flowNodeId: 'task-1',
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

  it('should handle operationId filter', () => {
    const {result} = renderHook(() => useBatchOperationMutationRequestBody(), {
      wrapper: getWrapper({
        operationId: 'op-123',
        active: 'true',
      }),
    });

    expect(result.current).toEqual({
      filter: {
        batchOperationId: {$eq: 'op-123'},
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
    });
  });
});
