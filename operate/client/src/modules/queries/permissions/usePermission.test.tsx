/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {usePermissions} from './usePermissions';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {PERMISSIONS} from 'modules/constants';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';

describe('usePermissions', () => {
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

  it('should return permissions from process instance when resourcePermissionsEnabled is true', async () => {
    const mockData: ProcessInstanceEntity = {
      id: 'processInstance1',
      permissions: ['READ'],
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

    window.clientConfig = {resourcePermissionsEnabled: true};

    mockFetchProcessInstance().withSuccess(mockData);

    const {result} = renderHook(() => usePermissions(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockData.permissions);
  });

  it('should return default permissions when resourcePermissionsEnabled is false', async () => {
    const mockData: ProcessInstanceEntity = {
      id: 'processInstance1',
      permissions: null,
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

    window.clientConfig = {resourcePermissionsEnabled: false};

    mockFetchProcessInstance().withSuccess(mockData);

    const {result} = renderHook(() => usePermissions(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(PERMISSIONS);
  });

  it('should handle server error while fetching permissions', async () => {
    mockFetchProcessInstance().withServerError();

    const {result} = renderHook(() => usePermissions(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('failed-response');
    expect(result.current.error?.response).toBeDefined();
  });

  it('should handle network error while fetching permissions', async () => {
    mockFetchProcessInstance().withNetworkError();

    const {result} = renderHook(() => usePermissions(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('network-error');
    expect(result.current.error?.response).toBeNull();
  });

  it('should handle empty processInstanceId', async () => {
    const {result} = renderHook(() => usePermissions(), {
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
