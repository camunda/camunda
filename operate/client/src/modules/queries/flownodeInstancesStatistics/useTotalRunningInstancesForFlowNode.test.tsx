/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {
  useTotalRunningInstancesForFlowNode,
  useTotalRunningInstancesForFlowNodes,
  useTotalRunningInstancesVisibleForFlowNode,
  useTotalRunningInstancesVisibleForFlowNodes,
} from './useTotalRunningInstancesForFlowNode';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {GetProcessInstanceStatisticsResponseBody} from '@vzeta/camunda-api-zod-schemas/operate';
import {mockProcessWithInputOutputMappingsXML} from 'modules/testUtils';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {Paths} from 'modules/Routes';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';

describe('useTotalRunningInstancesForFlowNode hooks', () => {
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
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should fetch total running instances for a single flow node', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          flowNodeId: 'StartEvent_1',
          active: 5,
          incidents: 2,
          completed: 0,
          canceled: 0,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(
      () => useTotalRunningInstancesForFlowNode('StartEvent_1'),
      {wrapper: Wrapper},
    );

    mockFetchProcessXML().withSuccess(mockProcessWithInputOutputMappingsXML);
    await processInstanceDetailsDiagramStore.fetchProcessXml('processId');

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toBe(7); // active + incidents
  });

  it('should fetch total running instances for multiple flow nodes', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          flowNodeId: 'StartEvent_1',
          active: 5,
          incidents: 2,
          completed: 0,
          canceled: 0,
        },
        {
          flowNodeId: 'Activity_0qtp1k6',
          active: 3,
          incidents: 1,
          completed: 0,
          canceled: 0,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(
      () =>
        useTotalRunningInstancesForFlowNodes([
          'StartEvent_1',
          'Activity_0qtp1k6',
        ]),
      {wrapper: Wrapper},
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toStrictEqual({
      Activity_0qtp1k6: 4,
      StartEvent_1: 7,
    }); // [active + incidents for node1, node2]
  });

  it('should fetch total visible running instances for a single flow node', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          flowNodeId: 'StartEvent_1',
          active: 5,
          incidents: 2,
          completed: 0,
          canceled: 0,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(
      () => useTotalRunningInstancesVisibleForFlowNode('StartEvent_1'),
      {wrapper: Wrapper},
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toBe(7); // filteredActive + incidents
  });

  it('should fetch total visible running instances for multiple flow nodes', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          flowNodeId: 'StartEvent_1',
          active: 5,
          incidents: 2,
          completed: 0,
          canceled: 0,
        },
        {
          flowNodeId: 'Activity_0qtp1k6',
          active: 3,
          incidents: 1,
          completed: 0,
          canceled: 0,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);

    const {result} = renderHook(
      () =>
        useTotalRunningInstancesVisibleForFlowNodes([
          'StartEvent_1',
          'Activity_0qtp1k6',
        ]),
      {wrapper: Wrapper},
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toStrictEqual({
      Activity_0qtp1k6: 4,
      StartEvent_1: 7,
    }); // [filteredActive + incidents for node1, node2]
  });

  it('should handle empty data', async () => {
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});

    const {result} = renderHook(
      () => useTotalRunningInstancesForFlowNode('StartEvent_1'),
      {wrapper: Wrapper},
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toBe(0);
  });

  it('should handle server error', async () => {
    mockFetchFlownodeInstancesStatistics().withServerError();

    const {result} = renderHook(
      () => useTotalRunningInstancesForFlowNode('StartEvent_1'),
      {wrapper: Wrapper},
    );

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('failed-response');
  });

  it('should handle network error', async () => {
    mockFetchFlownodeInstancesStatistics().withNetworkError();

    const {result} = renderHook(
      () => useTotalRunningInstancesForFlowNode('StartEvent_1'),
      {wrapper: Wrapper},
    );

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeDefined();
    expect(result.current.error?.variant).toBe('network-error');
  });

  it('should handle loading state', async () => {
    mockFetchFlownodeInstancesStatistics().withDelay({items: []});

    const {result} = renderHook(
      () => useTotalRunningInstancesForFlowNode('StartEvent_1'),
      {wrapper: Wrapper},
    );

    await waitFor(() => expect(result.current.isLoading).toBe(true));
  });
});
