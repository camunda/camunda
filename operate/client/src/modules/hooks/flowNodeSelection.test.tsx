/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {useWillAllFlowNodesBeCanceled} from './modifications';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {type GetProcessInstanceStatisticsResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {
  mockProcessWithInputOutputMappingsXML,
  createProcessInstance,
} from 'modules/testUtils';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';

const getWrapper = () => {
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
  return Wrapper;
};

describe('useWillAllFlowNodesBeCanceled', () => {
  beforeEach(() => {
    modificationsStore.reset();
    mockFetchProcessInstance().withSuccess(createProcessInstance());
  });

  it('should return true if all flow nodes are canceled', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          elementId: 'node1',
          active: 0,
          completed: 0,
          canceled: 1,
          incidents: 0,
        },
        {
          elementId: 'node2',
          active: 0,
          completed: 0,
          canceled: 1,
          incidents: 0,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );

    const {result} = renderHook(() => useWillAllFlowNodesBeCanceled(), {
      wrapper: getWrapper(),
    });

    await waitFor(() => expect(result.current).toBe(true));
  });

  it('should return false if there are ADD_TOKEN or MOVE_TOKEN modifications', async () => {
    modificationsStore.addModification({
      type: 'token',
      payload: {
        operation: 'ADD_TOKEN',
        flowNode: {id: 'node1', name: 'Node 1'},
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
        scopeId: 'scope1',
        parentScopeIds: {},
      },
    });

    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );

    const {result} = renderHook(() => useWillAllFlowNodesBeCanceled(), {
      wrapper: getWrapper(),
    });

    expect(result.current).toBe(false);
  });

  it('should return false if there are active flow nodes', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          elementId: 'node1',
          active: 1,
          completed: 0,
          canceled: 0,
          incidents: 0,
        },
        {
          elementId: 'node2',
          active: 0,
          completed: 0,
          canceled: 1,
          incidents: 0,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );

    const {result} = renderHook(() => useWillAllFlowNodesBeCanceled(), {
      wrapper: getWrapper(),
    });

    await waitFor(() => expect(result.current).toBe(false));
  });

  it('should return false if there are flow nodes with incidents', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          elementId: 'node1',
          active: 0,
          completed: 0,
          canceled: 0,
          incidents: 1,
        },
        {
          elementId: 'node2',
          active: 0,
          completed: 0,
          canceled: 1,
          incidents: 0,
        },
      ],
    };

    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );

    const {result} = renderHook(() => useWillAllFlowNodesBeCanceled(), {
      wrapper: getWrapper(),
    });

    await waitFor(() => expect(result.current).toBe(false));
  });

  it('should return false if there are no statistics', async () => {
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );

    const {result} = renderHook(() => useWillAllFlowNodesBeCanceled(), {
      wrapper: getWrapper(),
    });

    await waitFor(() => expect(result.current).toBe(false));
  });
});
