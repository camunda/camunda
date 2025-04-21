/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {useRootInstanceId} from './useRootInstanceId';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {GetProcessInstanceCallHierarchyResponseBody} from '@vzeta/camunda-api-zod-schemas/operate';

describe('useRootInstanceId', () => {
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

  it('should return the root instance ID when available', async () => {
    const mockData: GetProcessInstanceCallHierarchyResponseBody = {
      items: [
        {
          processInstanceKey: 'rootInstance1',
          processDefinitionName: 'Root Process',
        },
      ],
    };

    mockFetchCallHierarchy().withSuccess(mockData);

    const {result} = renderHook(() => useRootInstanceId(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toBe('rootInstance1');
  });

  it('should return undefined when no root instance is available', async () => {
    const mockData: GetProcessInstanceCallHierarchyResponseBody = {
      items: [],
    };

    mockFetchCallHierarchy().withSuccess(mockData);

    const {result} = renderHook(() => useRootInstanceId(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toBeUndefined();
  });

  it('should handle server error while fetching call hierarchy', async () => {
    mockFetchCallHierarchy().withServerError();

    const {result} = renderHook(() => useRootInstanceId(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('failed-response');
    expect(result.current.error?.response).toBeDefined();
  });

  it('should handle network error while fetching call hierarchy', async () => {
    mockFetchCallHierarchy().withNetworkError();

    const {result} = renderHook(() => useRootInstanceId(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('network-error');
    expect(result.current.error?.response).toBeNull();
  });

  it('should handle empty processInstanceId', async () => {
    const {result} = renderHook(() => useRootInstanceId(), {
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
