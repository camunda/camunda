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
  groupedProcessesMock,
  mockProcessStatisticsV2 as mockProcessStatistics,
  mockProcessInstances,
} from 'modules/testUtils';
import {DiagramPanel} from './index';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {useEffect, act} from 'react';
import {Paths} from 'modules/Routes';
import {batchModificationStore} from 'modules/stores/batchModification';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';

jest.mock('modules/utils/bpmn');

function getWrapper(initialPath: string = Paths.dashboard()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processXmlStore.reset();
        processesStore.reset();
        batchModificationStore.reset();
      };
    }, []);

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[initialPath]}>
          {children}
          <button onClick={batchModificationStore.enable}>
            Enable batch modification mode
          </button>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

describe('DiagramPanel', () => {
  const originalWindow = {...window};
  const locationSpy = jest.spyOn(window, 'location', 'get');

  beforeEach(() => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess('');

    processesStore.fetchProcesses();
  });

  afterEach(() => {
    locationSpy.mockClear();
  });

  it('should render header', async () => {
    const originalWindowPrompt = window.prompt;
    window.prompt = jest.fn();

    const queryString = '?process=bigVarProcess&version=1';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

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

    window.prompt = originalWindowPrompt;
  });

  it('should show the loading indicator, when diagram is loading', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    mockFetchProcessInstancesStatistics().withDelay(mockProcessStatistics);

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('diagram-spinner'));
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

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

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

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

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
    const consoleErrorMock = jest
      .spyOn(global.console, 'error')
      .mockImplementation();

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withServerError();

    render(<DiagramPanel />, {
      wrapper: getWrapper(),
    });

    await act(async () => {
      await processXmlStore.fetchProcessXml('1');
    });

    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
    expect(
      screen.queryByText(/There is no Process selected/),
    ).not.toBeInTheDocument();

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess('');

    await act(async () => {
      await processXmlStore.fetchProcessXml('2');
    });

    expect(
      screen.queryByText('Data could not be fetched'),
    ).not.toBeInTheDocument();

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withServerError();

    await act(async () => {
      await processXmlStore.fetchProcessXml('3');
    });

    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
    expect(
      screen.queryByText(/There is no Process selected/),
    ).not.toBeInTheDocument();

    consoleErrorMock.mockRestore();
  });

  it('should render diagram when statistics endpoint fails', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    mockFetchProcessInstancesStatistics().withServerError();
    mockFetchProcessXML().withSuccess('');

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(screen.queryByTestId(/^state-overlay/)).not.toBeInTheDocument();
  });

  it('should render statistics', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess('');

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(await screen.findByTestId('diagram')).toBeInTheDocument();
    expect(await screen.findByTestId(/^state-overlay/)).toBeInTheDocument();
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

  it('should handle error from useProcessInstancesOverlayData hook', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    mockFetchProcessInstancesStatistics().withServerError();
    mockFetchProcessXML().withSuccess('');

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();
  });

  it('should handle error from useBatchModificationOverlayData hook', async () => {
    const queryString = '?process=bigVarProcess&version=1';

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess('');
    mockFetchProcessInstancesStatistics().withServerError();

    render(<DiagramPanel />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(
      await screen.findByText('Data could not be fetched'),
    ).toBeInTheDocument();
  });
});
