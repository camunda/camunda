/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {
  createInstance,
  createMultiInstanceFlowNodeInstances,
} from 'modules/testUtils';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockFetchFlowNodeInstances} from 'modules/mocks/api/fetchFlowNodeInstances';
import {useEffect} from 'react';

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

  return <ThemeProvider>{children}</ThemeProvider>;
};

describe('FlowNodeInstanceLog', () => {
  beforeEach(async () => {
    mockFetchProcessInstance().withSuccess(
      createInstance({
        id: '1',
        state: 'ACTIVE',
        processName: 'processName',
        bpmnProcessId: 'processName',
      })
    );

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
      screen.getByTestId('instance-history-skeleton')
    );
  });

  it('should render skeleton when instance diagram is not loaded', async () => {
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchProcessXML().withSuccess('');
    flowNodeInstanceStore.init();
    processInstanceDetailsDiagramStore.fetchProcessXml('1');

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    expect(
      await screen.findByTestId('instance-history-skeleton')
    ).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-history-skeleton')
    );
  });

  it('should display error when instance tree data could not be fetched', async () => {
    mockFetchFlowNodeInstances().withServerError();
    mockFetchProcessXML().withSuccess('');
    flowNodeInstanceStore.init();
    processInstanceDetailsDiagramStore.fetchProcessXml('1');

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Instance History could not be fetched')
    ).toBeInTheDocument();
  });

  it('should display error when instance diagram could not be fetched', async () => {
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1);
    mockFetchProcessXML().withServerError();
    flowNodeInstanceStore.init();
    processInstanceDetailsDiagramStore.fetchProcessXml('1');

    render(<FlowNodeInstanceLog />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Instance History could not be fetched')
    ).toBeInTheDocument();
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
      })
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
      })
    );
    mockFetchFlowNodeInstances().withSuccess(processInstancesMock.level1Poll);

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(screen.queryByTestId('INCIDENT-icon')).not.toBeInTheDocument();
      expect(screen.getAllByTestId('COMPLETED-icon')).toHaveLength(2);
    });

    expect(
      screen.queryByText('Instance History could not be fetched')
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
      0
    );
  });
});
