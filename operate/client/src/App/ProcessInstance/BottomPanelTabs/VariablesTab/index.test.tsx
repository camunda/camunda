/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {Paths} from 'modules/Routes';
import {VariablesTab} from './index';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockFetchElementInstance} from 'modules/mocks/api/v2/elementInstances/fetchElementInstance';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {
  createVariable,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.10';

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [Paths.processInstance('1')],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={initialEntries}>
          <Routes>
            <Route path={Paths.processInstance()} element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  );

  return Wrapper;
};

const suspendedElementInstance = (
  type: ElementInstance['type'],
): ElementInstance => ({
  elementInstanceKey: '2',
  elementId: 'Activity_0qtp1k6',
  elementName: 'Activity',
  type,
  state: 'ACTIVE',
  startDate: '2018-06-21',
  endDate: null,
  processDefinitionId: 'someKey',
  processInstanceKey: '1',
  processDefinitionKey: '2',
  rootProcessInstanceKey: null,
  hasIncident: false,
  incidentKey: null,
  tenantId: '<default>',
});

describe('VariablesTab', () => {
  it('should display view full value button and not display edit button if instance is completed or canceled', async () => {
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstance().withSuccess({
      processInstanceKey: '1',
      state: 'TERMINATED',
      startDate: '2018-06-21',
      endDate: null,
      processDefinitionKey: '2',
      processDefinitionVersion: 1,
      processDefinitionVersionTag: null,
      processDefinitionId: 'someKey',
      tenantId: '<default>',
      processDefinitionName: 'someProcessName',
      hasIncident: false,
      parentProcessInstanceKey: null,
      parentElementInstanceKey: null,
      rootProcessInstanceKey: null,
      tags: [],
      businessId: null,
      suspendedDate: null,
    });
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<VariablesTab />, {wrapper: getWrapper()});

    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    expect(
      screen.queryByRole('button', {name: /edit variable/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /Open/i,
      }),
    ).toBeInTheDocument();
  });

  it('should display edit button for a service task scope on a suspended instance', async () => {
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockFetchProcessInstance().withSuccess({
      processInstanceKey: '1',
      state: 'SUSPENDED',
      startDate: '2018-06-21',
      endDate: null,
      processDefinitionKey: '2',
      processDefinitionVersion: 1,
      processDefinitionVersionTag: null,
      processDefinitionId: 'someKey',
      tenantId: '<default>',
      processDefinitionName: 'someProcessName',
      hasIncident: false,
      parentProcessInstanceKey: null,
      parentElementInstanceKey: null,
      rootProcessInstanceKey: null,
      tags: [],
      businessId: null,
      suspendedDate: null,
    });
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockSearchJobs().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockFetchElementInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'Activity_0qtp1k6',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ],
    });
    mockFetchElementInstance('2').withSuccess(
      suspendedElementInstance('SERVICE_TASK'),
    );

    render(<VariablesTab />, {
      wrapper: getWrapper([
        `${Paths.processInstance('1')}?elementId=Activity_0qtp1k6&elementInstanceKey=2`,
      ]),
    });

    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    expect(screen.getByRole('button', {name: /^edit$/i})).toBeInTheDocument();
  });

  it('should not display edit button for a user task scope on a suspended instance', async () => {
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockFetchProcessInstance().withSuccess({
      processInstanceKey: '1',
      state: 'SUSPENDED',
      startDate: '2018-06-21',
      endDate: null,
      processDefinitionKey: '2',
      processDefinitionVersion: 1,
      processDefinitionVersionTag: null,
      processDefinitionId: 'someKey',
      tenantId: '<default>',
      processDefinitionName: 'someProcessName',
      hasIncident: false,
      parentProcessInstanceKey: null,
      parentElementInstanceKey: null,
      rootProcessInstanceKey: null,
      tags: [],
      businessId: null,
      suspendedDate: null,
    });
    mockSearchVariables().withSuccess({
      items: [createVariable()],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockSearchJobs().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockFetchElementInstancesStatistics().withSuccess({
      items: [
        {
          elementId: 'Activity_0qtp1k6',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ],
    });
    mockFetchElementInstance('2').withSuccess(
      suspendedElementInstance('USER_TASK'),
    );

    render(<VariablesTab />, {
      wrapper: getWrapper([
        `${Paths.processInstance('1')}?elementId=Activity_0qtp1k6&elementInstanceKey=2`,
      ]),
    });

    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });

    expect(
      screen.queryByRole('button', {name: /edit variable/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /Open/i,
      }),
    ).toBeInTheDocument();
  });
});
