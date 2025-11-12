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

import {FlowNodeInstanceLog} from './index';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {
  createInstance,
  createMultiInstanceFlowNodeInstances,
  createOperation,
} from 'modules/testUtils';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {useEffect} from 'react';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {init} from 'modules/utils/flowNodeInstance';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';

vi.mock('modules/utils/bpmn');

const processInstancesMock = createMultiInstanceFlowNodeInstances('1');
const mockProcessInstance: ProcessInstance = {
  processInstanceKey: '1',
  state: 'ACTIVE',
  startDate: '2018-12-12',
  processDefinitionKey: 'processName',
  processDefinitionVersion: 1,
  processDefinitionId: 'processName',
  tenantId: '<default>',
  processDefinitionName: 'Multi-Instance Process',
  hasIncident: false,
};
const mockDeprecatedProcessInstance = createInstance({
  id: '1',
  state: 'ACTIVE',
  processName: 'processName',
  bpmnProcessId: 'processName',
  operations: [
    createOperation({
      state: 'COMPLETED',
      type: 'MIGRATE_PROCESS_INSTANCE',
      completedDate: MOCK_TIMESTAMP,
    }),
  ],
});

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  useEffect(() => {
    return () => {
      processInstanceDetailsStore.reset();
      flowNodeInstanceStore.reset();
    };
  }, []);

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

describe('FlowNodeInstanceLog', () => {
  beforeEach(async () => {
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockDeprecatedProcessInstance,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockDeprecatedProcessInstance,
    );
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});

    processInstanceDetailsStore.init({id: '1'});
  });

  it('should render skeleton when instance tree is not loaded', async () => {
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchProcessDefinitionXml().withSuccess('');
    init(mockProcessInstance);

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-history-skeleton'),
    );
  });

  it('should render skeleton when instance diagram is not loaded', async () => {
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchProcessDefinitionXml().withSuccess('');
    init(mockProcessInstance);

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-history-skeleton'),
    );
  });

  it('should display error when instance tree data could not be fetched', async () => {
    mockFetchFlowNodeInstances().withServerError();
    mockFetchProcessDefinitionXml().withSuccess('');
    init(mockProcessInstance);

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Instance History could not be fetched'),
    ).toBeInTheDocument();
  });

  it('should display error when instance diagram could not be fetched', async () => {
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchProcessDefinitionXml().withServerError();
    init(mockProcessInstance);

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Instance History could not be fetched'),
    ).toBeInTheDocument();
  });

  //TODO unskip when endpoint migrated
  it.skip('should display permissions error when access to the process definition is forbidden', async () => {
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchProcessDefinitionXml().withServerError(403);
    init(mockProcessInstance);

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

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
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockDeprecatedProcessInstance,
    );
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchFlownodeInstancesStatistics().withSuccess({items: []});

    vi.useFakeTimers({shouldAdvanceTime: true});
    init(mockProcessInstance);

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(
      screen.queryByTestId('instance-history-skeleton'),
    );

    expect(await screen.findAllByTestId('INCIDENT-icon')).toHaveLength(1);
    expect(await screen.findAllByTestId('COMPLETED-icon')).toHaveLength(1);

    // first poll (server error)
    mockFetchProcessInstanceDeprecated().withSuccess(
      createInstance({
        id: '1',
        state: 'ACTIVE',
        processName: 'processName',
        bpmnProcessId: 'processName',
      }),
    );
    mockFetchFlowNodeInstances().withServerError();
    vi.runOnlyPendingTimers();
    expect(
      await screen.findByText(/Instance History could not be fetched/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('tree', {name: /processName instance history/i}),
    ).not.toBeInTheDocument();

    // second poll (success)
    mockFetchProcessInstanceDeprecated().withSuccess(
      createInstance({
        id: '1',
        state: 'ACTIVE',
        processName: 'processName',
        bpmnProcessId: 'processName',
      }),
    );
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1Poll);
    vi.runOnlyPendingTimers();
    await waitForElementToBeRemoved(
      screen.queryByText(/Instance History could not be fetched/i),
    );

    expect(
      screen.getByRole('tree', {name: /processName instance history/i}),
    ).toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should render flow node instances tree', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockDeprecatedProcessInstance,
    );
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchProcessDefinitionXml().withSuccess('');
    init(mockProcessInstance);

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    expect((await screen.findAllByText('processName')).length).toBeGreaterThan(
      0,
    );

    expect(
      await screen.findByText('Migrated 2018-12-12 00:00:00'),
    ).toBeInTheDocument();
    vi.clearAllTimers();
    vi.useRealTimers();
  });
});
