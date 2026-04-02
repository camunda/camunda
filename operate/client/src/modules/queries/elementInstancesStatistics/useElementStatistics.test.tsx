/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {useElementStatistics} from './useElementStatistics';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {type GetProcessInstanceStatisticsResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {
  createProcessInstance,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';

describe('useElementStatistics', () => {
  const Wrapper = ({children}: {children: React.ReactNode}) => {
    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
            <Routes>
              <Route path={Paths.processInstance()} element={children} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };

  beforeEach(async () => {
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockFetchProcessInstance().withSuccess(createProcessInstance());
  });

  it('should fetch parsed element statistics successfully', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          elementId: 'StartEvent_1',
          active: 5,
          completed: 10,
          canceled: 0,
          incidents: 0,
        },
        {
          elementId: 'Activity_0qtp1k6',
          active: 3,
          completed: 7,
          canceled: 0,
          incidents: 0,
        },
      ],
    };

    mockFetchElementInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(() => useElementStatistics(), {
      wrapper: Wrapper,
    });

    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual([
      {id: 'StartEvent_1', count: 5, elementState: 'active'},
      {id: 'StartEvent_1', count: 10, elementState: 'completed'},
      {id: 'Activity_0qtp1k6', count: 3, elementState: 'active'},
      {id: 'Activity_0qtp1k6', count: 7, elementState: 'completed'},
    ]);
  });

  it('should handle server error while fetching element statistics', async () => {
    mockFetchElementInstancesStatistics().withServerError();

    const {result} = renderHook(() => useElementStatistics(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('failed-response');
    expect(result.current.error?.response).toBeDefined();
  });

  it('should handle network error while fetching element statistics', async () => {
    mockFetchElementInstancesStatistics().withNetworkError();

    const {result} = renderHook(() => useElementStatistics(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('network-error');
    expect(result.current.error?.response).toBeNull();
  });

  it('should handle empty data', async () => {
    mockFetchElementInstancesStatistics().withSuccess({items: []});

    const {result} = renderHook(() => useElementStatistics(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual([]);
  });

  it('should handle loading state', async () => {
    mockFetchElementInstancesStatistics().withDelay({items: []});

    const {result} = renderHook(() => useElementStatistics(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current.isLoading).toBe(true));
  });
});
