/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
import {Route, MemoryRouter, Routes, Link} from 'react-router-dom';
import {ListView} from '../index';
import {
  groupedProcessesMock,
  mockProcessStatistics,
  mockProcessXML,
  mockProcessInstances,
  mockProcessInstancesWithOperation,
} from 'modules/testUtils';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {processesStore} from 'modules/stores/processes/processes.list';
import {LocationLog} from 'modules/utils/LocationLog';
import {AppHeader} from 'App/Layout/AppHeader';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstancesStatistics';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {mockFetchBatchOperations} from 'modules/mocks/api/fetchBatchOperations';
import {notificationsStore} from 'modules/stores/notifications';
import {processStatisticsStore} from 'modules/stores/processStatistics/processStatistics.list';

jest.mock('modules/utils/bpmn');
const handleRefetchSpy = jest.spyOn(processesStore, 'handleRefetch');
jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));

function getWrapper(initialPath: string = Paths.processes()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processInstancesSelectionStore.reset();
        processInstancesStore.reset();
        processXmlStore.reset();
        processStatisticsStore.reset();
        processesStore.reset();
      };
    }, []);
    return (
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path={Paths.processes()} element={children} />
        </Routes>
        <Link to={`${Paths.processes()}?active=true`}>go to active</Link>
        <Link
          to={`${Paths.processes()}?process=eventBasedGatewayProcess&version=1`}
        >
          go to event based
        </Link>
        <Link to={Paths.processes()}>go to no filters</Link>
        <LocationLog />
      </MemoryRouter>
    );
  };

  return Wrapper;
}

describe('Instances', () => {
  beforeEach(() => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessInstancesStatistics().withSuccess(mockProcessStatistics);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchBatchOperations().withSuccess([]);
  });

  it('should render title and document title', async () => {
    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}?incidents=true&active=true`),
    });

    expect(screen.getByText('Operate Process Instances')).toBeInTheDocument();
    expect(document.title).toBe('Operate: Process Instances');
    expect(
      await screen.findByText('There is no Process selected'),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('heading', {
        name: /process instances - 912 results/i,
      }),
    ).toBeInTheDocument();
  });

  it('should render page components', async () => {
    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}?active=true&incidents=true`),
    });

    // diagram panel
    expect(
      screen.getByRole('heading', {name: 'Process', level: 2}),
    ).toBeInTheDocument();

    // filters panel
    expect(
      screen.getByRole('heading', {name: 'Process', level: 3}),
    ).toBeInTheDocument();

    expect(
      await screen.findByText('There is no Process selected'),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see a Diagram, select a Process in the Filters panel',
      ),
    ).toBeInTheDocument();

    // filters panel
    expect(screen.getByRole('heading', {name: /Filter/})).toBeInTheDocument();

    // instances table
    expect(
      await screen.findByRole('heading', {
        name: /process instances - 912 results/i,
      }),
    ).toBeInTheDocument();
  });

  it('should reset selected instances when filters change', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);

    const {user} = render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}?active=true&incidents=true`),
    });

    await waitForElementToBeRemoved(screen.getByTestId('data-table-skeleton'));

    const withinRow = within(
      screen.getByRole('row', {
        name: /2251799813685594/i,
      }),
    );

    expect(
      withinRow.getByRole('checkbox', {name: /select row/i}),
    ).not.toBeChecked();

    await user.click(withinRow.getByRole('checkbox', {name: /select row/i}));
    expect(
      withinRow.getByRole('checkbox', {name: /select row/i}),
    ).toBeChecked();

    mockFetchProcessInstances().withDelay(mockProcessInstances);
    await user.click(screen.getByText(/go to active/i));
    await waitForElementToBeRemoved(screen.getByTestId('data-table-loader'));

    expect(
      withinRow.getByRole('checkbox', {name: /select row/i}),
    ).not.toBeChecked();
  });

  it('should not reset selected instances when table is sorted', async () => {
    const {user} = render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}?active=true&incidents=true`),
    });

    expect(
      await screen.findByRole('heading', {
        name: /process instances - 912 results/i,
      }),
    ).toBeInTheDocument();

    const withinRow = within(
      screen.getByRole('row', {
        name: /2251799813685594/i,
      }),
    );

    expect(
      withinRow.getByRole('checkbox', {name: /select row/i}),
    ).not.toBeChecked();

    await user.click(withinRow.getByRole('checkbox', {name: /select row/i}));
    expect(
      withinRow.getByRole('checkbox', {name: /select row/i}),
    ).toBeChecked();

    mockFetchProcessInstances().withDelay(mockProcessInstances);
    await user.click(screen.getByRole('button', {name: 'Sort by Name'}));

    expect(screen.getByTestId('data-table-loader')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('data-table-loader'));

    expect(
      withinRow.getByRole('checkbox', {name: /select row/i}),
    ).toBeChecked();
  });

  it('should fetch diagram and diagram statistics', async () => {
    const firstProcessStatisticsResponse = [
      {...mockProcessStatistics[0]!, completed: 10},
    ];

    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockFetchProcessInstancesStatistics().withSuccess(
      firstProcessStatisticsResponse,
    );

    render(<ListView />, {
      wrapper: getWrapper(
        `${Paths.processes()}?process=bigVarProcess&version=1`,
      ),
    });

    await waitFor(() => expect(processXmlStore.state.status).toBe('fetched'));
    expect(processXmlStore.state.xml).toBe(mockProcessXML);
    expect(processXmlStore.state.diagramModel).not.toBe(null);

    await waitFor(() =>
      expect(processStatisticsStore.state.statistics).toEqual(
        firstProcessStatisticsResponse,
      ),
    );
  });

  it('should refetch data when navigated from header', async () => {
    const {user} = render(
      <>
        <AppHeader />
        <ListView />
      </>,
      {
        wrapper: getWrapper(`${Paths.processes()}?active=true&incidents=true`),
      },
    );

    await waitForElementToBeRemoved(screen.getByTestId('data-table-skeleton'));

    await waitFor(() =>
      expect(screen.queryByTestId('diagram-spinner')).not.toBeInTheDocument(),
    );

    mockFetchProcessInstances().withDelay(mockProcessInstances);
    mockFetchGroupedProcesses().withDelay(groupedProcessesMock);

    await user.click(
      within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        }),
      ).getByRole('link', {
        name: /processes/i,
      }),
    );
    expect(await screen.findByTestId('diagram-spinner')).toBeInTheDocument();
    expect(await screen.findByTestId('data-table-loader')).toBeInTheDocument();

    await waitFor(() =>
      expect(screen.queryByTestId('diagram-spinner')).not.toBeInTheDocument(),
    );

    await waitFor(() =>
      expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument(),
    );
  });

  it('should poll 3 times for grouped processes and redirect to initial processes page if process does not exist', async () => {
    jest.useFakeTimers();

    const queryString =
      '?active=true&incidents=true&process=non-existing-process&version=all';

    const originalWindow = {...window};

    const locationSpy = jest.spyOn(window, 'location', 'get');

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    expect(screen.getByTestId('search').textContent).toBe(queryString);

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    await waitFor(() => expect(processesStore.state.status).toBe('fetching'));
    expect(handleRefetchSpy).toHaveBeenCalledTimes(1);

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    jest.runOnlyPendingTimers();

    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(2));

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    jest.runOnlyPendingTimers();
    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(3));

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);

    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    jest.runOnlyPendingTimers();

    await waitFor(() => {
      expect(processesStore.processes.length).toBe(5);
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes/);
      expect(screen.getByTestId('search').textContent).toBe(
        '?active=true&incidents=true',
      );
    });

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'error',
      title: 'Process could not be found',
    });

    jest.clearAllTimers();
    jest.useRealTimers();

    locationSpy.mockRestore();
  });

  it('should hide Operation State column when Operation Id filter is not set', async () => {
    mockFetchProcessInstances().withSuccess(mockProcessInstancesWithOperation);

    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}`),
    });

    expect(screen.queryByText('Operation State')).not.toBeInTheDocument();
  });

  it('should show Operation State column when Operation Id filter is set', async () => {
    const queryString = '?operationId=f4be6304-a0e0-4976-b81b-7a07fb4e96e5';
    const originalWindow = {...window};
    const locationSpy = jest.spyOn(window, 'location', 'get');
    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    mockFetchProcessInstances().withSuccess(mockProcessInstancesWithOperation);
    await waitForElementToBeRemoved(screen.getByTestId('data-table-skeleton'));

    expect(screen.getByText('Operation State')).toBeInTheDocument();

    locationSpy.mockRestore();
  });

  it('should show correct error message when error row is expanded', async () => {
    const queryString = '?operationId=f4be6304-a0e0-4976-b81b-7a07fb4e96e5';
    const originalWindow = {...window};
    const locationSpy = jest.spyOn(window, 'location', 'get');

    locationSpy.mockImplementation(() => ({
      ...originalWindow.location,
      search: queryString,
    }));

    const {user} = render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    mockFetchProcessInstances().withSuccess(mockProcessInstancesWithOperation);

    await waitForElementToBeRemoved(screen.getByTestId('data-table-skeleton'));

    expect(screen.getByText('0000000000000002')).toBeInTheDocument();
    expect(
      screen.queryByText('Batch Operation Error Message'),
    ).not.toBeVisible();

    const withinRow = within(
      screen.getByRole('row', {
        name: /0000000000000002/i,
      }),
    );

    const expandButton = withinRow.getByRole('button', {
      name: 'Expand current row',
    });
    await user.click(expandButton);

    expect(screen.getByText('Batch Operation Error Message')).toBeVisible();

    locationSpy.mockRestore();
  });
});
