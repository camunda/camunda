/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {useIsProcessInstanceRunning} from './useIsProcessInstanceRunning';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {ProcessInstance} from '@vzeta/camunda-api-zod-schemas/operate';

describe('useIsProcessInstanceRunning', () => {
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

  it('should return true if the process instance is running (ACTIVE state)', async () => {
    const mockData: ProcessInstance = {
      processInstanceKey: 'processInstance1',
      state: 'ACTIVE',
      startDate: '2023-01-01T00:00:00.000Z',
      processDefinitionKey: 'processDefinition1',
      processDefinitionVersion: 0,
      processDefinitionId: '',
      tenantId: '',
      processDefinitionName: '',
      hasIncident: false,
    };

    mockFetchProcessInstance().withSuccess(mockData);

    const {result} = renderHook(() => useIsProcessInstanceRunning(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toBe(true);
  });

  it('should return true if the process instance is running (INCIDENT state)', async () => {
    const mockData: ProcessInstance = {
      processInstanceKey: 'processInstance1',
      state: 'ACTIVE',
      startDate: '2023-01-01T00:00:00.000Z',
      processDefinitionKey: 'processDefinition1',
      processDefinitionVersion: 0,
      processDefinitionId: '',
      tenantId: '',
      processDefinitionName: '',
      hasIncident: true,
    };

    mockFetchProcessInstance().withSuccess(mockData);

    const {result} = renderHook(() => useIsProcessInstanceRunning(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toBe(true);
  });

  it('should return false if the process instance is not running (COMPLETED state)', async () => {
    const mockData: ProcessInstance = {
      processInstanceKey: 'processInstance1',
      state: 'COMPLETED',
      startDate: '2023-01-01T00:00:00.000Z',
      processDefinitionKey: 'processDefinition1',
      processDefinitionVersion: 0,
      processDefinitionId: '',
      tenantId: '',
      processDefinitionName: '',
      hasIncident: false,
    };

    mockFetchProcessInstance().withSuccess(mockData);

    const {result} = renderHook(() => useIsProcessInstanceRunning(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toBe(false);
  });

  it('should handle server error while fetching process instance', async () => {
    mockFetchProcessInstance().withServerError();

    const {result} = renderHook(() => useIsProcessInstanceRunning(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('failed-response');
    expect(result.current.error?.response).toBeDefined();
  });

  it('should handle network error while fetching process instance', async () => {
    mockFetchProcessInstance().withNetworkError();

    const {result} = renderHook(() => useIsProcessInstanceRunning(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('network-error');
    expect(result.current.error?.response).toBeNull();
  });

  it('should handle empty processInstanceId', async () => {
    const {result} = renderHook(() => useIsProcessInstanceRunning(), {
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
