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
import {
  mockProcessDefinitions,
  mockProcessStatisticsV2 as mockProcessStatistics,
  mockProcessInstances,
  mockMultipleStatesStatistics,
  mockProcessXML,
  mockProcessInstancesV2,
} from 'modules/testUtils';
import {DiagramPanel} from './index';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {processesStore} from 'modules/stores/processes/processes.list';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {batchModificationStore} from 'modules/stores/batchModification';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {ProcessDefinitionKeyContext} from '../../processDefinitionKeyContext';

vi.mock('modules/utils/bpmn');
vi.mock('modules/bpmn-js/utils/isProcessEndEvent', () => ({
  isProcessOrSubProcessEndEvent: vi.fn(() => true),
}));

function getWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      processInstancesSelectionStore.init();
      return () => {
        processesStore.reset();
        batchModificationStore.reset();
        processInstancesSelectionStore.reset();
      };
    }, []);

    return (
      <ProcessDefinitionKeyContext.Provider value={'123'}>
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={[initialPath]}>
            {children}
            <button onClick={batchModificationStore.enable}>
              Enable batch modification mode
            </button>
            <button
              onClick={() =>
                processInstancesSelectionStore.selectProcessInstance('0')
              }
            >
              Select process instance
            </button>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };

  return Wrapper;
}

describe('DiagramPanel', () => {
  beforeEach(() => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessDefinitionXml().withSuccess('');
    mockSearchProcessInstances().withSuccess(mockProcessInstancesV2);

    processesStore.fetchProcesses();
  });

  it('should render header', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });
    vi.stubGlobal('prompt', vi.fn());

    const {user} = render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(await screen.findByText('Big variable process')).toBeInTheDocument();
    expect(screen.getByText('bigVarProcess')).toBeInTheDocument();
    expect(screen.getByText(/MyVersionTag/i)).toBeInTheDocument();

    await waitFor(async () => {
      expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    });

    await user.click(
      screen.getByRole('button', {
        name: 'Process ID / Click to copy',
      }),
    );
    expect(await screen.findByText('Copied to clipboard')).toBeInTheDocument();
  });

  it('should show the loading indicator, when diagram is loading', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    mockFetchProcessInstancesStatistics().withDelay(mockProcessStatistics);

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();

    await waitForElementToBeRemoved(screen.queryByTestId('diagram-spinner'));
    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
  });

  it('should show an empty state message when no process is selected', async () => {
    render(<DiagramPanel />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByText('There is no Process selected'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see a Diagram, select a Process in the Filters panel',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });

  it('should show a message when no process version is selected', async () => {
    const queryString = '?process=bigVarProcess&version=all';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(
      await screen.findByText(
        'There is more than one Version selected for Process "Big variable process"',
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText('To see a Diagram, select a single Version'),
    ).toBeInTheDocument();

    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });

  it('should display bpmnProcessId as process name in the message when no process version is selected', async () => {
    const queryString = '?process=eventBasedGatewayProcess&version=all';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(
      await screen.findByText(
        'There is more than one Version selected for Process "eventBasedGatewayProcess"',
      ),
    ).toBeInTheDocument();
  });

  it('should show an error message', async () => {
    const consoleErrorMock = vi
      .spyOn(global.console, 'error')
      .mockImplementation(() => {});

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessDefinitionXml().withServerError();

    render(<DiagramPanel />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(/There is no Process selected/),
    ).not.toBeInTheDocument();

    consoleErrorMock.mockRestore();
  });

  it('should render statistics', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessDefinitionXml().withSuccess('');

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(await screen.findByTestId(/^state-overlay/)).toBeInTheDocument();
  });

  it('should not fetch batch modification data outside batch modification mode', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    const mockProcessInstancesStatisticsResolver = vi.fn();
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics, {
      mockResolverFn: mockProcessInstancesStatisticsResolver,
    });
    mockFetchProcessDefinitionXml().withSuccess('');

    const {user} = render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    // Expect fetching initial statistics
    await waitFor(() =>
      expect(mockProcessInstancesStatisticsResolver).toHaveBeenCalledTimes(1),
    );

    await user.click(
      screen.getByRole('button', {name: /select process instance/i}),
    );

    // Expect no fetching outside batch modification mode
    await waitFor(() =>
      expect(mockProcessInstancesStatisticsResolver).toHaveBeenCalledTimes(1),
    );

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics, {
      mockResolverFn: mockProcessInstancesStatisticsResolver,
    });
    await user.click(
      screen.getByRole('button', {name: /enable batch modification mode/i}),
    );
    await user.click(
      screen.getByRole('button', {name: /select process instance/i}),
    );

    // Expect fetching inside batch modification mode
    await waitFor(() =>
      expect(mockProcessInstancesStatisticsResolver).toHaveBeenCalledTimes(2),
    );
  });

  it('should render batch modification notification', async () => {
    const {user} = render(<DiagramPanel />, {
      wrapper: getWrapper(),
    });

    const notificationText =
      'Please select where you want to move the selected instances on the diagram.';

    expect(screen.queryByText(notificationText)).not.toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {name: /enable batch modification mode/i}),
    );

    expect(await screen.findByText(notificationText)).toBeInTheDocument();
  });

  it('should still render diagram when useProcessInstancesOverlayData fails', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    mockFetchProcessInstancesStatistics().withServerError();
    mockFetchProcessDefinitionXml().withSuccess('');

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.queryByTestId(/^state-overlay/)).not.toBeInTheDocument();
  });

  it('should still render diagram when useBatchModificationOverlayData fails', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstancesStatistics().withServerError();

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.queryByTestId(/^state-overlay/)).not.toBeInTheDocument();
  });

  it('should display statistics when active and incidents are selected in the filter', async () => {
    const queryString =
      '?process=bigVarProcess&version=1&active=true&incidents=true';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    mockFetchProcessInstancesStatistics().withSuccess(
      mockMultipleStatesStatistics,
    );
    mockFetchProcessDefinitionXml().withSuccess('');

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(
      await screen.findByTestId('state-overlay-EndEvent_042s0oc-active'),
    ).toHaveTextContent('1');
    expect(
      await screen.findByTestId('state-overlay-EndEvent_042s0oc-incidents'),
    ).toHaveTextContent('3');
  });

  it('should display statistics when completed and canceled are selected in the filter', async () => {
    const queryString =
      '?process=bigVarProcess&version=1&completed=true&canceled=true';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    mockFetchProcessInstancesStatistics().withSuccess(
      mockMultipleStatesStatistics,
    );
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(
      await screen.findByTestId('state-overlay-EndEvent_042s0oc-canceled'),
    ).toHaveTextContent('2');
    expect(
      await screen.findByTestId(
        'state-overlay-EndEvent_042s0oc-completedEndEvents',
      ),
    ).toHaveTextContent('4');
  });

  it('should display statistics when all states are selected', async () => {
    const queryString =
      '?process=bigVarProcess&version=1&active=true&incidents=true&completed=true&canceled=true';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    mockFetchProcessInstancesStatistics().withSuccess(
      mockMultipleStatesStatistics,
    );
    mockFetchProcessDefinitionXml().withSuccess('');

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(
      await screen.findByTestId('state-overlay-EndEvent_042s0oc-active'),
    ).toHaveTextContent('1');
    expect(
      await screen.findByTestId('state-overlay-EndEvent_042s0oc-canceled'),
    ).toHaveTextContent('2');
    expect(
      await screen.findByTestId('state-overlay-EndEvent_042s0oc-incidents'),
    ).toHaveTextContent('3');
    expect(
      await screen.findByTestId(
        'state-overlay-EndEvent_042s0oc-completedEndEvents',
      ),
    ).toHaveTextContent('4');
  });
});
