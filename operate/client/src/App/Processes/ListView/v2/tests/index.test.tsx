/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor, within} from 'modules/testing-library';
import {Route, MemoryRouter, Routes, Link} from 'react-router-dom';
import {ListView} from '../index';
import {
  mockProcessDefinitions,
  mockProcessXML,
  mockProcessInstancesV2 as mockProcessInstances,
  createUser,
  searchResult,
  createProcessInstance,
} from 'modules/testUtils';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelectionV2';
import {processesStore} from 'modules/stores/processes/processes.list';
import {LocationLog} from 'modules/utils/LocationLog';
import {AppHeader} from 'App/Layout/AppHeader';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {act, useEffect} from 'react';
import {Paths} from 'modules/Routes';
import {mockQueryBatchOperations} from 'modules/mocks/api/v2/batchOperations/queryBatchOperations';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {notificationsStore} from 'modules/stores/notifications';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {QueryClientProvider} from '@tanstack/react-query';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockMe} from 'modules/mocks/api/v2/me';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';
import {mockFetchProcessInstancesStatistics} from 'modules/mocks/api/v2/processInstances/fetchProcessInstancesStatistics';

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

const mockProcessInstancesV2WithOperation = {
  items: [
    createProcessInstance({
      processInstanceKey: '0000000000000002',
      processDefinitionKey: '2251799813685612',
      processDefinitionId: 'someKey',
      processDefinitionName: 'someProcessName',
      state: 'ACTIVE',
    }),
  ],
  page: {
    totalItems: 1,
  },
};

const mockBatchOperationItemsWithFailure = {
  items: [
    {
      batchOperationKey: 'f4be6304-a0e0-4976-b81b-7a07fb4e96e5',
      itemKey: 'item-key-1',
      processInstanceKey: '0000000000000002',
      state: 'FAILED' as const,
      operationType: 'MODIFY_PROCESS_INSTANCE' as const,
      errorMessage: 'Batch Operation Error Message',
    },
  ],
  page: {
    totalItems: 1,
  },
};

describe('Instances', () => {
  beforeEach(() => {
    mockSearchProcessDefinitions().withSuccess(searchResult([]));
    mockSearchProcessDefinitions().withSuccess(searchResult([]));
    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);
    mockSearchProcessInstances().withSuccess(mockProcessInstances);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockFetchProcessInstancesStatistics().withSuccess({
      items: [],
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

    expect(
      screen.getByRole('region', {name: 'Diagram Panel'}),
    ).toBeInTheDocument();

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

    expect(screen.getByRole('heading', {name: /Filter/})).toBeInTheDocument();

    expect(
      await screen.findByRole('heading', {
        name: /process instances - 912 results/i,
      }),
    ).toBeInTheDocument();
  });

  it('should reset selected instances when filters change', async () => {
    mockSearchProcessInstances().withSuccess(mockProcessInstances);

    const {user} = render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}?active=true&incidents=true`),
    });

    await screen.findByRole('heading', {
      name: /process instances - 912 results/i,
    });

    const withinRow = within(
      screen.getByRole('row', {
        name: /2251799813685594/i,
      }),
    );

    const checkbox = withinRow.getByRole('checkbox');
    expect(checkbox).not.toBeChecked();

    await user.click(checkbox);
    expect(checkbox).toBeChecked();

    mockSearchProcessInstances().withSuccess(mockProcessInstances);
    await user.click(screen.getByText(/go to active/i));

    const updatedRow = await screen.findByRole('row', {
      name: /2251799813685594/i,
    });

    const updatedCheckbox = within(updatedRow).getByRole('checkbox');

    expect(updatedCheckbox).not.toBeChecked();
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

    mockSearchProcessInstances().withDelay(mockProcessInstances);
    await user.click(screen.getByRole('button', {name: 'Sort by Name'}));

    await waitFor(() => {
      expect(screen.queryByTestId('data-table-loader')).not.toBeInTheDocument();
    });

    const updatedRow = await screen.findByRole('row', {
      name: /2251799813685594/i,
    });
    const updatedCheckbox = within(updatedRow).getByRole('checkbox');

    expect(updatedCheckbox).toBeChecked();
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

    await screen.findByRole('heading', {
      name: /process instances - 912 results/i,
    });

    await waitFor(() =>
      expect(screen.queryByTestId('diagram-spinner')).not.toBeInTheDocument(),
    );

    mockSearchProcessInstances().withDelay(mockProcessInstances);
    mockSearchProcessDefinitions().withDelay(mockProcessDefinitions);

    await user.click(
      await within(
        screen.getByRole('navigation', {
          name: /camunda operate/i,
        }),
      ).findByRole('link', {
        name: /processes/i,
      }),
    );

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

    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(screen.getByTestId('search').textContent).toBe(queryString);

    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    vi.runOnlyPendingTimers();

    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(1));

    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    act(() => {
      vi.runOnlyPendingTimers();
    });

    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(2));

    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    vi.runOnlyPendingTimers();
    await waitFor(() => expect(handleRefetchSpy).toHaveBeenCalledTimes(3));

    mockSearchProcessDefinitions().withSuccess(mockProcessDefinitions);
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    mockSearchProcessInstances().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

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
    mockSearchProcessInstances().withSuccess(
      mockProcessInstancesV2WithOperation,
    );

    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}?active=true`),
    });

    await screen.findByRole('heading', {
      name: /process instances - 1 result/i,
    });

    expect(screen.queryByText('Operation State')).not.toBeInTheDocument();
  });

  it('should show Operation State column when Operation Id filter is set', async () => {
    const queryString = '?operationId=f4be6304-a0e0-4976-b81b-7a07fb4e96e5';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    mockSearchProcessInstances().withSuccess(
      mockProcessInstancesV2WithOperation,
    );
    mockQueryBatchOperationItems().withSuccess(
      mockBatchOperationItemsWithFailure,
    );

    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    await screen.findByRole('heading', {
      name: /process instances - 1 result/i,
    });

    expect(screen.getByText('Operation State')).toBeInTheDocument();
  });

  it('should show correct error message when error row is expanded', async () => {
    const queryString = '?operationId=f4be6304-a0e0-4976-b81b-7a07fb4e96e5';

    vi.stubGlobal('location', {
      ...window.location,
      search: queryString,
    });

    mockSearchProcessInstances().withSuccess(
      mockProcessInstancesV2WithOperation,
    );
    mockQueryBatchOperationItems().withSuccess(
      mockBatchOperationItemsWithFailure,
    );

    const {user} = render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    await screen.findByRole('heading', {
      name: /process instances - 1 result/i,
    });

    await waitFor(() => {
      expect(screen.queryByText('Loading...')).not.toBeInTheDocument();
    });

    expect(screen.getByText('0000000000000002')).toBeInTheDocument();

    await waitFor(() => {
      expect(
        screen.getByText('Batch Operation Error Message'),
      ).toBeInTheDocument();
    });
    expect(screen.getByText('Batch Operation Error Message')).not.toBeVisible();

    const withinRow = within(
      screen.getByRole('row', {name: /0000000000000002/i}),
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

    mockSearchProcessInstances().withSuccess(
      mockProcessInstancesV2WithOperation,
    );
    mockQueryBatchOperationItems().withSuccess(
      mockBatchOperationItemsWithFailure,
    );

    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    await screen.findByRole('heading', {
      name: /process instances - 1 result/i,
    });

    await waitFor(() => {
      expect(screen.queryByText('Loading...')).not.toBeInTheDocument();
    });

    const withinRow = within(
      screen.getByRole('row', {name: /0000000000000002/i}),
    );

    expect(withinRow.getByText('FAILED')).toBeInTheDocument();
  });
});
