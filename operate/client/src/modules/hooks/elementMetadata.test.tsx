/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from '@testing-library/react';
import {QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {useHasMultipleInstances} from './elementMetadata';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {
  createProcessInstance,
  mockProcessWithInputOutputMappingsXML,
  searchResult,
} from 'modules/testUtils';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {Paths} from 'modules/Routes';
import type {
  ElementInstance,
  GetProcessInstanceStatisticsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';

const PROCESS_INSTANCE_ID = '123';

const getWrapper = (searchParams: Record<string, string> = {}) => {
  const params = new URLSearchParams(searchParams);
  const url = `${Paths.processInstance(PROCESS_INSTANCE_ID)}?${params.toString()}`;

  const Wrapper = ({children}: {children: React.ReactNode}) => (
    <ProcessDefinitionKeyContext.Provider value="456">
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[url]}>
          <Routes>
            <Route path={Paths.processInstance()} element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  );

  return Wrapper;
};

const multipleInstancesStatistics: GetProcessInstanceStatisticsResponseBody = {
  items: [
    {
      elementId: 'multiTask',
      active: 2,
      completed: 1,
      canceled: 0,
      incidents: 0,
    },
  ],
};

const singleInstanceStatistics: GetProcessInstanceStatisticsResponseBody = {
  items: [
    {
      elementId: 'multiTask',
      active: 1,
      completed: 0,
      canceled: 0,
      incidents: 0,
    },
  ],
};

const mockElementInstance: ElementInstance = {
  elementInstanceKey: '123456789',
  elementId: 'Task_1',
  elementName: 'Service Task',
  type: 'SERVICE_TASK',
  state: 'COMPLETED',
  startDate: '2023-01-15T10:00:00.000Z',
  endDate: '2023-01-15T10:05:00.000Z',
  processDefinitionId: 'process-def-1',
  processInstanceKey: '111222333',
  processDefinitionKey: '444555666',
  hasIncident: false,
  tenantId: '<default>',
  rootProcessInstanceKey: null,
  incidentKey: null,
};

describe('useHasMultipleInstances', () => {
  beforeEach(() => {
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockFetchProcessInstance().withSuccess(
      createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID}),
    );
  });

  it('should return true when a multi-instance element is selected with multiple instances', async () => {
    mockFetchElementInstancesStatistics().withSuccess(
      multipleInstancesStatistics,
    );
    mockSearchElementInstances().withSuccess(
      searchResult([
        mockElementInstance,
        mockElementInstance,
        mockElementInstance,
      ]),
    );

    const {result} = renderHook(() => useHasMultipleInstances(), {
      wrapper: getWrapper({
        elementId: 'multiTask',
        isMultiInstanceBody: 'true',
      }),
    });

    await waitFor(() => expect(result.current).toBe(true));
  });

  it('should return false when a multi-instance element is selected with a single instance', async () => {
    mockFetchElementInstancesStatistics().withSuccess(singleInstanceStatistics);
    mockSearchElementInstances().withSuccess(
      searchResult([mockElementInstance]),
    );

    const {result} = renderHook(() => useHasMultipleInstances(), {
      wrapper: getWrapper({
        elementId: 'multiTask',
        isMultiInstanceBody: 'true',
      }),
    });

    await waitFor(() => expect(result.current).toBe(false));
  });

  it('should return false when a specific instance is selected', async () => {
    mockFetchElementInstancesStatistics().withSuccess(
      multipleInstancesStatistics,
    );
    mockFetchElementInstance('999').withSuccess({
      elementInstanceKey: '999',
      elementId: 'multiTask',
      elementName: 'Multi Task',
      type: 'MULTI_INSTANCE_BODY',
      state: 'ACTIVE',
      startDate: '2024-01-01',
      processDefinitionId: 'proc-def-1',
      processInstanceKey: PROCESS_INSTANCE_ID,
      processDefinitionKey: '456',
      hasIncident: false,
      tenantId: '<default>',
      endDate: null,
      rootProcessInstanceKey: null,
      incidentKey: null,
    });

    const {result} = renderHook(() => useHasMultipleInstances(), {
      wrapper: getWrapper({
        elementId: 'multiTask',
        elementInstanceKey: '999',
        isMultiInstanceBody: 'true',
      }),
    });

    await waitFor(() => expect(result.current).toBe(false));
  });

  it('should return true for elements with multiple instances', async () => {
    mockFetchElementInstancesStatistics().withSuccess(
      multipleInstancesStatistics,
    );
    mockSearchElementInstances().withSuccess(
      searchResult([
        mockElementInstance,
        mockElementInstance,
        mockElementInstance,
      ]),
    );

    const {result} = renderHook(() => useHasMultipleInstances(), {
      wrapper: getWrapper({
        elementId: 'multiTask',
      }),
    });

    await waitFor(() => expect(result.current).toBe(true));
  });

  it('should return false for elements with a single instance', async () => {
    mockFetchElementInstancesStatistics().withSuccess(singleInstanceStatistics);
    mockSearchElementInstances().withSuccess(
      searchResult([mockElementInstance]),
    );

    const {result} = renderHook(() => useHasMultipleInstances(), {
      wrapper: getWrapper({
        elementId: 'multiTask',
      }),
    });

    await waitFor(() => expect(result.current).toBe(false));
  });
});
