/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {useOperations} from './useOperations';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';

describe('useOperations', () => {
  const Wrapper = ({children}: {children: React.ReactNode}) => {
    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
          <Routes>
            <Route path={Paths.processInstance()} element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should fetch operations successfully', async () => {
    const mockData: ProcessInstanceEntity = {
      id: 'processInstance1',
      operations: [
        {
          id: 'operation1',
          type: 'CANCEL_PROCESS_INSTANCE',
          state: 'COMPLETED',
          errorMessage: null,
          completedDate: null,
        },
        {
          id: 'operation2',
          type: 'MODIFY_PROCESS_INSTANCE',
          state: 'COMPLETED',
          errorMessage: null,
          completedDate: null,
        },
      ],
      processId: '',
      processName: '',
      processVersion: 0,
      startDate: '',
      endDate: null,
      state: 'COMPLETED',
      bpmnProcessId: '',
      hasActiveOperation: false,
      sortValues: [],
      parentInstanceId: null,
      rootInstanceId: null,
      callHierarchy: [],
      tenantId: '',
    };

    mockFetchProcessInstance().withSuccess(mockData);

    const {result} = renderHook(() => useOperations(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockData.operations);
  });

  it('should handle server error while fetching operations', async () => {
    mockFetchProcessInstance().withServerError();

    const {result} = renderHook(() => useOperations(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('failed-response');
    expect(result.current.error?.response).toBeDefined();
  });

  it('should handle network error while fetching operations', async () => {
    mockFetchProcessInstance().withNetworkError();

    const {result} = renderHook(() => useOperations(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('network-error');
    expect(result.current.error?.response).toBeNull();
  });

  it('should handle empty operations', async () => {
    const mockData: ProcessInstanceEntity = {
      id: 'processInstance1',
      operations: [],
      processId: '',
      processName: '',
      processVersion: 0,
      startDate: '',
      endDate: null,
      state: 'COMPLETED',
      bpmnProcessId: '',
      hasActiveOperation: false,
      sortValues: [],
      parentInstanceId: null,
      rootInstanceId: null,
      callHierarchy: [],
      tenantId: '',
    };

    mockFetchProcessInstance().withSuccess(mockData);

    const {result} = renderHook(() => useOperations(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual([]);
  });

  it('should handle empty processInstanceId', async () => {
    const {result} = renderHook(() => useOperations(), {
      wrapper: ({children}) => (
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={[Paths.processInstance(undefined)]}>
            <Routes>
              <Route path={Paths.processInstance()} element={children} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      ),
    });

    expect(result.current.isFetched).toBe(false);
    expect(result.current.data).toBeUndefined();
  });
});
