/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {useSelectableFlowNodes} from './useSelectableFlowNodes';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import * as pageParamsModule from 'App/ProcessInstance/useProcessInstancePageParams';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {GetProcessInstanceStatisticsResponseBody} from '@vzeta/camunda-api-zod-schemas/operate';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {useEffect} from 'react';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockProcessWithInputOutputMappingsXML} from 'modules/testUtils';

describe('useSelectableFlowNodes', () => {
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

  it('should fetch selectable flow nodes successfully', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          flowNodeId: 'StartEvent_1',
          active: 5,
          completed: 10,
          canceled: 0,
          incidents: 0,
        },
        {
          flowNodeId: 'Activity_0qtp1k6',
          active: 3,
          completed: 7,
          canceled: 0,
          incidents: 0,
        },
        {
          flowNodeId: 'Gateway_1',
          active: 0,
          completed: 0,
          canceled: 0,
          incidents: 0,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(() => useSelectableFlowNodes(), {
      wrapper: Wrapper,
    });

    expect(result.current.isLoading).toBe(true);

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual([
      'StartEvent_1',
      'Activity_0qtp1k6',
      'Gateway_1',
    ]);
  });

  it('should handle server error while fetching selectable flow nodes', async () => {
    mockFetchFlownodeInstancesStatistics().withServerError();

    const {result} = renderHook(() => useSelectableFlowNodes(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('failed-response');
    expect(result.current.error?.response).toBeDefined();
  });

  it('should handle network error while fetching selectable flow nodes', async () => {
    mockFetchFlownodeInstancesStatistics().withNetworkError();

    const {result} = renderHook(() => useSelectableFlowNodes(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('network-error');
    expect(result.current.error?.response).toBeNull();
  });

  it('should handle empty data', async () => {
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});

    const {result} = renderHook(() => useSelectableFlowNodes(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual([]);
  });

  it('should handle loading state', async () => {
    const {result} = renderHook(() => useSelectableFlowNodes(), {
      wrapper: Wrapper,
    });

    expect(result.current.isLoading).toBe(true);
  });
});
