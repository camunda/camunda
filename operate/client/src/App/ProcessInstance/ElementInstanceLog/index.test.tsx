/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';

import {ElementInstanceLog} from './index';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {mockFetchElementInstancesStatistics} from 'modules/mocks/api/v2/elementInstances/elementInstancesStatistics/fetchElementInstancesStatistics';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';

vi.mock('modules/utils/bpmn');

const mockProcessInstance: ProcessInstance = {
  processInstanceKey: '1',
  state: 'ACTIVE',
  startDate: '2018-12-12',
  endDate: null,
  processDefinitionKey: 'processName',
  processDefinitionVersion: 1,
  processDefinitionVersionTag: null,
  processDefinitionId: 'processName',
  tenantId: '<default>',
  processDefinitionName: 'Multi-Instance Process',
  hasIncident: false,
  parentProcessInstanceKey: null,
  parentElementInstanceKey: null,
  rootProcessInstanceKey: null,
  tags: [],
};

const mockElementInstances = {
  items: [
    {
      elementInstanceKey: '2251799813686130',
      processInstanceKey: '1',
      processDefinitionKey: 'processName',
      processDefinitionId: 'processName',
      state: 'ACTIVE' as const,
      type: 'START_EVENT' as const,
      elementId: 'StartEvent_1',
      elementName: 'Start Event',
      hasIncident: true,
      incidentKey: null,
      tenantId: '<default>',
      startDate: '2018-12-12T00:00:00.000+0000',
      endDate: '2018-12-12T00:00:01.000+0000',
      rootProcessInstanceKey: null,
    },
    {
      elementInstanceKey: '2251799813686156',
      processInstanceKey: '1',
      processDefinitionKey: 'processName',
      processDefinitionId: 'processName',
      state: 'COMPLETED' as const,
      type: 'SERVICE_TASK' as const,
      elementId: 'ServiceTask_1',
      elementName: 'Service Task',
      hasIncident: false,
      incidentKey: null,
      tenantId: '<default>',
      startDate: '2018-12-12T00:00:02.000+0000',
      endDate: '2018-12-12T00:00:03.000+0000',
      rootProcessInstanceKey: null,
    },
  ],
  page: {
    totalItems: 2,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
};

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  return (
    <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <Routes>
            <Route path={Paths.processInstance()} element={children} />
          </Routes>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    </MemoryRouter>
  );
};

describe('ElementInstanceLog', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchElementInstancesStatistics().withSuccess({items: []});
  });

  it('should render skeleton when instance tree is not loaded', async () => {
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchElementInstances().withSuccess(mockElementInstances);
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<ElementInstanceLog />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-history-skeleton'),
    );
  });

  it('should render skeleton when instance diagram is not loaded', async () => {
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchElementInstances().withSuccess(mockElementInstances);
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<ElementInstanceLog />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-history-skeleton'),
    );
  });

  it('should display error when instance tree data could not be fetched', async () => {
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchElementInstances().withServerError();
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<ElementInstanceLog />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Instance History could not be fetched'),
    ).toBeInTheDocument();
  });

  it('should display error when instance diagram could not be fetched', async () => {
    mockFetchProcessDefinitionXml().withServerError();
    mockSearchElementInstances().withSuccess(mockElementInstances);
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<ElementInstanceLog />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Instance History could not be fetched'),
    ).toBeInTheDocument();
  });

  it('should display permissions error when access to the process definition is forbidden', async () => {
    mockFetchProcessDefinitionXml().withServerError(403);
    mockSearchElementInstances().withSuccess(mockElementInstances);
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<ElementInstanceLog />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Missing permissions to access Instance History'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Please contact your organization owner or admin to give you the necessary permissions to access this instance history',
      ),
    ).toBeInTheDocument();
  });

  it('should display permissions error when element instances search returns 403', async () => {
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchElementInstances().withServerError(403);
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<ElementInstanceLog />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Missing permissions to access Instance History'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Please contact your organization owner or admin to give you the necessary permissions to access this instance history',
      ),
    ).toBeInTheDocument();
  });

  it('should continue polling after poll failure', async () => {
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchElementInstancesStatistics().withSuccess({items: []});
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    mockSearchElementInstances().withServerError();

    vi.useFakeTimers({shouldAdvanceTime: true});

    render(<ElementInstanceLog />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Instance History could not be fetched'),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('tree', {name: /processName instance history/i}),
    ).not.toBeInTheDocument();

    mockSearchElementInstances().withSuccess(mockElementInstances);

    vi.runOnlyPendingTimers();
    await waitForElementToBeRemoved(
      screen.queryByText(/Instance History could not be fetched/i),
    );

    expect(
      screen.getByRole('tree', {
        name: /Multi-Instance Process instance history/i,
      }),
    ).toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should render element instances tree', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchElementInstances().withSuccess(mockElementInstances);
    mockQueryBatchOperationItems().withSuccess({
      items: [
        {
          itemKey: '1',
          batchOperationKey: 'op-1',
          processInstanceKey: '1',
          state: 'COMPLETED',
          operationType: 'RESOLVE_INCIDENT',
          processedDate: '2018-12-12T00:00:00.000+0000',
          rootProcessInstanceKey: null,
          errorMessage: null,
        },
      ],
      page: {
        totalItems: 1,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });

    render(<ElementInstanceLog />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Multi-Instance Process'),
    ).toBeInTheDocument();

    expect(
      await screen.findByText('Migrated 2018-12-12 00:00:00'),
    ).toBeInTheDocument();
    vi.clearAllTimers();
    vi.useRealTimers();
  });
});
