/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {useProcessInstancesStatistics} from './useProcessInstancesStatistics';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 0,
    },
  },
});

describe('useProcessInstancesStatistics', () => {
  const wrapper = ({children}: {children: React.ReactNode}) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  it('should fetch process instances statistics successfully', async () => {
    const mockData = [
      {
        flowNodeId: 'messageCatchEvent',
        active: 2,
        canceled: 1,
        incidents: 3,
        completed: 4,
      },
    ];
    mockFetchProcessInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(() => useProcessInstancesStatistics({}), {
      wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockData);
  });

  it('should handle error while fetching process instances statistics', async () => {
    mockFetchProcessInstancesStatistics().withServerError();

    const {result} = renderHook(() => useProcessInstancesStatistics({}), {
      wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toEqual(new Error('an error occurred'));
  });
});
