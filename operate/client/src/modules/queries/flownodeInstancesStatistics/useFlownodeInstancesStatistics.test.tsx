/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {useFlownodeInstancesStatistics} from './useFlownodeInstancesStatistics';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import * as pageParamsModule from 'App/ProcessInstance/useProcessInstancePageParams';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {GetProcessInstanceStatisticsResponseBody} from '@vzeta/camunda-api-zod-schemas/operate';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {useEffect} from 'react';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockProcessWithInputOutputMappingsXML} from 'modules/testUtils';

describe('useFlownodeInstancesStatistics', () => {
  const Wrapper = ({children}: {children: React.ReactNode}) => {
    useEffect(() => {
      return () => {
        processInstanceDetailsDiagramStore.reset();
      };
    }, []);

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        {children}
      </QueryClientProvider>
    );
  };

  beforeEach(async () => {
    jest
      .spyOn(pageParamsModule, 'useProcessInstancePageParams')
      .mockReturnValue({processInstanceId: 'processInstanceId123'});

    mockFetchProcessXML().withSuccess(mockProcessWithInputOutputMappingsXML);
    await processInstanceDetailsDiagramStore.fetchProcessXml('processId');
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should fetch flownode instances statistics successfully', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          flowNodeId: 'node1',
          active: 5,
          completed: 10,
          canceled: 0,
          incidents: 0,
        },
        {
          flowNodeId: 'node2',
          active: 3,
          completed: 7,
          canceled: 0,
          incidents: 0,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(() => useFlownodeInstancesStatistics(), {
      wrapper: Wrapper,
    });

    expect(result.current.isLoading).toBe(true);

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockData);
  });

  it('should handle select', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          flowNodeId: 'node1',
          active: 5,
          completed: 10,
          canceled: 0,
          incidents: 0,
        },
        {
          flowNodeId: 'node2',
          active: 3,
          completed: 7,
          canceled: 0,
          incidents: 0,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(
      () => useFlownodeInstancesStatistics((data) => data.items.length),
      {
        wrapper: Wrapper,
      },
    );

    expect(result.current.isLoading).toBe(true);

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockData.items.length);
  });

  it('should handle server error while fetching flownode instances overlay statistics', async () => {
    mockFetchFlownodeInstancesStatistics().withServerError();

    const {result} = renderHook(() => useFlownodeInstancesStatistics(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('failed-response');
    expect(result.current.error?.response).toBeDefined();
  });

  it('should handle network error while fetching flownode instances overlay statistics', async () => {
    mockFetchFlownodeInstancesStatistics().withNetworkError();

    const {result} = renderHook(() => useFlownodeInstancesStatistics(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('network-error');
    expect(result.current.error?.response).toBeNull();
  });

  it('should handle empty data', async () => {
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});

    const {result} = renderHook(() => useFlownodeInstancesStatistics(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual({items: []});
  });

  it('should handle loading state', async () => {
    const {result} = renderHook(() => useFlownodeInstancesStatistics(), {
      wrapper: Wrapper,
    });

    expect(result.current.isLoading).toBe(true);
  });

  it('should not fetch data when enabled is false', async () => {
    const {result} = renderHook(
      () => useFlownodeInstancesStatistics((data) => data, false),
      {
        wrapper: Wrapper,
      },
    );

    expect(result.current.isLoading).toBe(false);
    expect(result.current.isFetched).toBe(false);
    expect(result.current.data).toBeUndefined();
  });
});
