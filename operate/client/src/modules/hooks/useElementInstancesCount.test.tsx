/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {useElementInstancesCount} from './useElementInstancesCount';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {type GetProcessInstanceStatisticsResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {
  createProcessInstance,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';

describe('useElementInstancesCount', () => {
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

  it('should return null when no elementId is provided', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          elementId: 'node1',
          active: 5,
          completed: 10,
          canceled: 2,
          incidents: 1,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(() => useElementInstancesCount(), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current).toBeNull());
  });

  it('should return null when element is not found in statistics', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          elementId: 'node1',
          active: 5,
          completed: 10,
          canceled: 2,
          incidents: 1,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(() => useElementInstancesCount('nonexistent'), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current).toBeNull());
  });

  it('should return correct sum of all instance counts', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          elementId: 'node1',
          active: 5,
          completed: 10,
          canceled: 2,
          incidents: 1,
        },
        {
          elementId: 'node2',
          active: 3,
          completed: 7,
          canceled: 0,
          incidents: 2,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(() => useElementInstancesCount('node1'), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current).toBe(18));
  });

  it('should return correct count for different element', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          elementId: 'node1',
          active: 5,
          completed: 10,
          canceled: 2,
          incidents: 1,
        },
        {
          elementId: 'node2',
          active: 3,
          completed: 7,
          canceled: 0,
          incidents: 2,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(() => useElementInstancesCount('node2'), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current).toBe(12));
  });

  it('should return 0 when there are no instances', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          elementId: 'node1',
          active: 0,
          completed: 0,
          canceled: 0,
          incidents: 0,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(() => useElementInstancesCount('node1'), {
      wrapper: Wrapper,
    });

    await waitFor(() => expect(result.current).toBe(0));
  });
});
