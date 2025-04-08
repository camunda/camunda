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
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';

import {FlowNodeInstanceLog} from './index';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {
  createInstance,
  createMultiInstanceFlowNodeInstances,
  createOperation,
} from 'modules/testUtils';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {useEffect} from 'react';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

jest.mock('modules/utils/bpmn');

const processInstancesMock = createMultiInstanceFlowNodeInstances('1');

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  useEffect(() => {
    return () => {
      processInstanceDetailsStore.reset();
      flowNodeInstanceStore.reset();
      processInstanceDetailsDiagramStore.reset();
    };
  }, []);

  return (
    <ProcessDefinitionKeyContext.Provider value="123">
      <QueryClientProvider client={getMockQueryClient()}>
        {children}
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  );
};

describe('FlowNodeInstanceLog', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(
      createInstance({
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
      }),
    );
    mockFetchProcessDefinitionXml().withSuccess('');

    processInstanceDetailsStore.init({id: '1'});
  });

  it('should render skeleton when instance tree is not loaded', async () => {
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchProcessXML().withSuccess('');
    flowNodeInstanceStore.init();
    processInstanceDetailsDiagramStore.fetchProcessXml('1');

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-history-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-history-skeleton'),
    );
  });

  it('should render skeleton when instance diagram is not loaded', async () => {
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchProcessXML().withSuccess('');
    flowNodeInstanceStore.init();
    processInstanceDetailsDiagramStore.fetchProcessXml('1');

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    expect(
      await screen.findByTestId('instance-history-skeleton'),
    ).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-history-skeleton'),
    );
  });

  it('should display error when instance tree data could not be fetched', async () => {
    mockFetchFlowNodeInstances().withServerError();
    mockFetchProcessXML().withSuccess('');
    flowNodeInstanceStore.init();
    processInstanceDetailsDiagramStore.fetchProcessXml('1');

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Instance History could not be fetched'),
    ).toBeInTheDocument();
  });

  it('should display error when instance diagram could not be fetched', async () => {
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchProcessXML().withServerError();
    flowNodeInstanceStore.init();
    processInstanceDetailsDiagramStore.fetchProcessXml('1');

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Instance History could not be fetched'),
    ).toBeInTheDocument();

    await waitFor(() =>
      expect(processInstanceDetailsDiagramStore.state.status).toBe('error'),
    );
  });

  it('should continue polling after poll failure', async () => {
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchProcessXML().withSuccess('');
    jest.useFakeTimers();
    flowNodeInstanceStore.init();
    processInstanceDetailsDiagramStore.fetchProcessXml('1');

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    expect(await screen.findAllByTestId('INCIDENT-icon')).toHaveLength(1);
    expect(await screen.findAllByTestId('COMPLETED-icon')).toHaveLength(1);

    // first poll
    mockFetchProcessInstance().withSuccess(
      createInstance({
        id: '1',
        state: 'ACTIVE',
        processName: 'processName',
        bpmnProcessId: 'processName',
      }),
    );
    mockFetchFlowNodeInstances().withServerError();

    jest.runOnlyPendingTimers();

    // second poll
    mockFetchProcessInstance().withSuccess(
      createInstance({
        id: '1',
        state: 'ACTIVE',
        processName: 'processName',
        bpmnProcessId: 'processName',
      }),
    );
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1Poll);

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(screen.queryByTestId('INCIDENT-icon')).not.toBeInTheDocument();
      expect(screen.getAllByTestId('COMPLETED-icon')).toHaveLength(2);
    });

    expect(
      screen.queryByText('Instance History could not be fetched'),
    ).not.toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should render flow node instances tree', async () => {
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchProcessXML().withSuccess('');
    flowNodeInstanceStore.init();
    processInstanceDetailsDiagramStore.fetchProcessXml('1');

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    expect((await screen.findAllByText('processName')).length).toBeGreaterThan(
      0,
    );

    expect(
      screen.getByText('Migrated 2018-12-12 00:00:00'),
    ).toBeInTheDocument();
  });
});
