/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {useProcessInstanceDeprecated} from './useProcessInstanceDeprecated';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';

describe('useProcessInstanceDeprecated', () => {
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

  it('should fetch process instance successfully', async () => {
    const mockData: ProcessInstanceEntity = {
      id: 'processInstance1',
      operations: [],
      processId: 'process1',
      processName: 'Test Process',
      processVersion: 1,
      startDate: '2023-01-01T00:00:00.000Z',
      endDate: null,
      state: 'ACTIVE',
      bpmnProcessId: 'bpmnProcess1',
      hasActiveOperation: false,
      sortValues: [],
      parentInstanceId: null,
      rootInstanceId: null,
      callHierarchy: [],
      tenantId: 'tenant1',
    };

    mockFetchProcessInstance().withSuccess(mockData);

    const {result} = renderHook(() => useProcessInstanceDeprecated(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockData);
  });

  it('should apply select function correctly', async () => {
    const mockData: ProcessInstanceEntity = {
      id: 'processInstance1',
      operations: [],
      processId: 'process1',
      processName: 'Test Process',
      processVersion: 1,
      startDate: '2023-01-01T00:00:00.000Z',
      endDate: null,
      state: 'ACTIVE',
      bpmnProcessId: 'bpmnProcess1',
      hasActiveOperation: false,
      sortValues: [],
      parentInstanceId: null,
      rootInstanceId: null,
      callHierarchy: [],
      tenantId: 'tenant1',
    };

    mockFetchProcessInstance().withSuccess(mockData);

    const select = (data: ProcessInstanceEntity) => data.processName;

    const {result} = renderHook(() => useProcessInstanceDeprecated(select), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toBe('Test Process');
  });

  it('should handle server error while fetching process instance', async () => {
    mockFetchProcessInstance().withServerError();

    const {result} = renderHook(() => useProcessInstanceDeprecated(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('failed-response');
    expect(result.current.error?.response).toBeDefined();
  });

  it('should handle network error while fetching process instance', async () => {
    mockFetchProcessInstance().withNetworkError();

    const {result} = renderHook(() => useProcessInstanceDeprecated(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('network-error');
    expect(result.current.error?.response).toBeNull();
  });

  it('should handle empty processInstanceId', async () => {
    const {result} = renderHook(() => useProcessInstanceDeprecated(), {
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
