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
  within,
} from 'modules/testing-library';
import {Route, MemoryRouter, Routes, Link} from 'react-router-dom';
import {ListView} from '../index';
import {
  groupedProcessesMock,
  mockProcessXML,
  mockProcessInstances,
  mockProcessInstancesWithOperation,
  createUser,
} from 'modules/testUtils';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';
import {processesStore} from 'modules/stores/processes/processes.list';
import {LocationLog} from 'modules/utils/LocationLog';
import {AppHeader} from 'App/Layout/AppHeader';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {mockFetchGroupedProcesses} from 'modules/mocks/api/processes/fetchGroupedProcesses';
import {act, useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {mockQueryBatchOperations} from 'modules/mocks/api/v2/batchOperations/queryBatchOperations';
import {notificationsStore} from 'modules/stores/notifications';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockMe} from 'modules/mocks/api/v2/me';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

function getWrapper(initialPath: string = Paths.processes()) {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processInstancesSelectionStore.reset();
        processInstancesStore.reset();
        processesStore.reset();
      };
    }, []);
    return (
      <QueryClientProvider client={getMockQueryClient()}>
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
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

describe('Instances', () => {
  beforeEach(() => {
    mockFetchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockMe().withSuccess(createUser({authorizedComponents: ['operate']}));
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
      screen.getByRole('region', {name: 'Diagram Panel'}),
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

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

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
    await waitForElementToBeRemoved(screen.queryByTestId('data-table-loader'));

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
    await waitForElementToBeRemoved(screen.queryByTestId('data-table-loader'));

    expect(
      withinRow.getByRole('checkbox', {name: /select row/i}),
    ).toBeChecked();
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

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    await waitFor(() =>
      expect(screen.queryByTestId('diagram-spinner')).not.toBeInTheDocument(),
    );

    mockFetchProcessInstances().withDelay(mockProcessInstances);
    mockFetchGroupedProcesses().withDelay(groupedProcessesMock);

    await user.click(
      await within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        }),
      ).findByRole('link', {
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
    const handleRefetchSpy = vi.spyOn(processesStore, 'handleRefetch');
    vi.useFakeTimers({shouldAdvanceTime: true});

    const queryString =
      '?active=true&incidents=true&process=non-existing-process&version=all';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    expect(screen.getByTestId('search').textContent).toBe(queryString);

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    vi.runOnlyPendingTimers();

    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(1));

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    act(() => {
      vi.runOnlyPendingTimers();
    });

    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(2));

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    vi.runOnlyPendingTimers();
    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(3));

    mockFetchGroupedProcesses().withSuccess(groupedProcessesMock);
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    mockFetchProcessInstances().withSuccess({
      processInstances: [],
      totalCount: 0,
    });

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    vi.runOnlyPendingTimers();

    await waitFor(() => {
      expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes/);
    });

    await waitFor(() =>
      expect(screen.getByTestId('search').textContent).toBe(
        '?active=true&incidents=true',
      ),
    );

    expect(processesStore.processes.length).toBe(5);

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'error',
      title: 'Process could not be found',
    });

    vi.clearAllTimers();
    vi.useRealTimers();
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
    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    mockFetchProcessInstances().withSuccess(mockProcessInstancesWithOperation);
    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByText('Operation State')).toBeInTheDocument();
  });

  it('should show correct error message when error row is expanded', async () => {
    const queryString = '?operationId=f4be6304-a0e0-4976-b81b-7a07fb4e96e5';
    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    const {user} = render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    mockFetchProcessInstances().withSuccess(mockProcessInstancesWithOperation);

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

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
  });

  it('should display correct operation from process instance with multiple operations', async () => {
    const queryString = '?operationId=f4be6304-a0e0-4976-b81b-7a07fb4e96e5';
    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    mockFetchProcessInstances().withSuccess(mockProcessInstancesWithOperation);
    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    const withinRow = within(
      screen.getByRole('row', {
        name: /0000000000000002/i,
      }),
    );

    expect(withinRow.getByText('FAILED')).toBeInTheDocument();
    expect(withinRow.queryByText('COMPLETED')).not.toBeInTheDocument();
  });
});
