/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {useProcessInstancesElementStates} from './useElementStates';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import * as filterModule from 'modules/hooks/useProcessInstanceStatisticsFilters';

vi.mock('modules/hooks/useFilters');
vi.mock('modules/hooks/useProcessInstanceStatisticsFilters');

describe('useProcessInstancesElementStates', () => {
  const wrapper = ({children}: {children: React.ReactNode}) => (
    <QueryClientProvider client={getMockQueryClient()}>
      {children}
    </QueryClientProvider>
  );

  beforeEach(() => {
    vi.spyOn(
      filterModule,
      'useProcessInstanceStatisticsFilters',
    ).mockReturnValue({filter: {}});
  });

  it('should fetch flow node states successfully', async () => {
    const mockData = {
      items: [
        {
          elementId: 'task1',
          active: 5,
          canceled: 2,
          incidents: 1,
          completed: 3,
        },
        {
          elementId: 'task2',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 4,
        },
      ],
    };

    const expectedParsedData = [
      {elementId: 'task1', count: 5, elementState: 'active'},
      {elementId: 'task1', count: 1, elementState: 'incidents'},
      {elementId: 'task1', count: 2, elementState: 'canceled'},
      {elementId: 'task1', count: 3, elementState: 'completed'},
      {elementId: 'task2', count: 4, elementState: 'completed'},
    ];

    mockFetchProcessInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(
      () => useProcessInstancesElementStates({}, 'processId'),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(expectedParsedData);
  });

  it('should handle server error while fetching flow node states', async () => {
    mockFetchProcessInstancesStatistics().withServerError();

    const {result} = renderHook(
      () => useProcessInstancesElementStates({}, 'processId'),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('failed-response');
    expect(result.current.error?.response).toBeDefined();
  });

  it('should handle network error while fetching flow node states', async () => {
    mockFetchProcessInstancesStatistics().withNetworkError();

    const {result} = renderHook(
      () => useProcessInstancesElementStates({}, 'processId'),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('network-error');
    expect(result.current.error?.response).toBeNull();
  });

  it('should handle empty data', async () => {
    mockFetchProcessInstancesStatistics().withSuccess({
      items: [],
    });

    const {result} = renderHook(
      () => useProcessInstancesElementStates({}, 'processId'),
      {
        wrapper,
      },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual([]);
  });

  it('should handle loading state', async () => {
    mockFetchProcessInstancesStatistics().withDelay({
      items: [],
    });

    const {result} = renderHook(
      () => useProcessInstancesElementStates({}, 'processId'),
      {
        wrapper,
      },
    );

    expect(result.current.isLoading).toBe(true);
  });

  it('should not fetch data when enabled is false', async () => {
    const {result} = renderHook(
      () => useProcessInstancesElementStates({}, 'processId', false),
      {
        wrapper,
      },
    );

    expect(result.current.isLoading).toBe(false);
    expect(result.current.isFetched).toBe(false);
    expect(result.current.data).toBeUndefined();
  });
});
