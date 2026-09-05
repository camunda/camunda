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
import {processInstancesSelectionStore} from 'modules/stores/instancesSelection';
import {LocationLog} from 'modules/utils/LocationLog';
import {AppHeader} from 'App/Layout/AppHeader';
import {mockSearchProcessInstances} from 'modules/mocks/api/v2/processInstances/searchProcessInstances';
import {useEffect} from 'react';
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
import {
  resumeProcessInstancesBatchOperationRequestBodySchema,
  suspendProcessInstancesBatchOperationRequestBodySchema,
  type QueryProcessInstancesResponseBody,
  type ResumeProcessInstancesBatchOperationRequestBody,
  type SuspendProcessInstancesBatchOperationRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {mockSuspendProcessInstancesBatchOperation} from 'modules/mocks/api/v2/processes/suspendProcessInstancesBatchOperation';
import {mockResumeProcessInstancesBatchOperation} from 'modules/mocks/api/v2/processes/resumeProcessInstancesBatchOperation';

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
            to={`${Paths.processes()}?processDefinitionId=eventBasedGatewayProcess&processDefinitionVersion=1`}
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
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
} satisfies QueryProcessInstancesResponseBody;

const mockBatchOperationItemsWithFailure = {
  items: [
    {
      batchOperationKey: 'f4be6304-a0e0-4976-b81b-7a07fb4e96e5',
      itemKey: 'item-key-1',
      processInstanceKey: '0000000000000002',
      rootProcessInstanceKey: null,
      state: 'FAILED' as const,
      processedDate: null,
      operationType: 'MODIFY_PROCESS_INSTANCE' as const,
      errorMessage: 'Batch Operation Error Message',
    },
  ],
  page: {
    totalItems: 1,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
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
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {
        totalItems: 0,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: false,
      },
    });
    mockFetchProcessInstancesStatistics().withSuccess({
      items: [],
    });
    mockMe().withSuccess(createUser({authorizedComponents: ['operate']}));
  });

  it('should display suspended instances when the suspended filter is selected', async () => {
    const suspendedInstance = createProcessInstance({
      processInstanceKey: 'suspended-instance',
      processDefinitionName: 'Suspended Process',
      state: 'SUSPENDED',
    });
    mockSearchProcessInstances().withSuccess(searchResult([suspendedInstance]));

    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}?suspended=true`),
    });

    expect(await screen.findByText('Suspended Process')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', {name: 'Suspended'})).toBeChecked();
  });

  it('should display active, suspended, and incident instances together', async () => {
    mockSearchProcessInstances().withSuccess(
      searchResult([
        createProcessInstance({
          processInstanceKey: 'active-instance',
          processDefinitionName: 'Active Process',
          state: 'ACTIVE',
          hasIncident: false,
        }),
        createProcessInstance({
          processInstanceKey: 'suspended-instance',
          processDefinitionName: 'Suspended Process',
          state: 'SUSPENDED',
        }),
        createProcessInstance({
          processInstanceKey: 'incident-instance',
          processDefinitionName: 'Incident Process',
          state: 'ACTIVE',
          hasIncident: true,
        }),
      ]),
    );

    render(<ListView />, {
      wrapper: getWrapper(
        `${Paths.processes()}?active=true&suspended=true&incidents=true`,
      ),
    });

    expect(await screen.findByText('Active Process')).toBeInTheDocument();
    expect(screen.getByText('Suspended Process')).toBeInTheDocument();
    expect(screen.getByText('Incident Process')).toBeInTheDocument();
  });

  it('should suspend selected active process instances', async () => {
    const activeInstance = createProcessInstance({
      processInstanceKey: 'active-instance',
      processDefinitionName: 'Active Process',
      state: 'ACTIVE',
      hasIncident: false,
    });
    const suspendedInstance = createProcessInstance({
      processInstanceKey: 'suspended-instance',
      processDefinitionName: 'Suspended Process',
      state: 'SUSPENDED',
    });
    const activeChildInstance = createProcessInstance({
      processInstanceKey: 'active-child-instance',
      processDefinitionName: 'Active Child Process',
      state: 'ACTIVE',
      parentProcessInstanceKey: 'parent-instance',
    });
    const completedInstance = createProcessInstance({
      processInstanceKey: 'completed-instance',
      processDefinitionName: 'Completed Process',
      state: 'COMPLETED',
    });
    mockSearchProcessInstances().withSuccess(
      searchResult([
        activeInstance,
        suspendedInstance,
        activeChildInstance,
        completedInstance,
      ]),
    );
    let requestBody:
      SuspendProcessInstancesBatchOperationRequestBody | undefined;
    const requestBodyResolver = vi.fn((body: unknown) => {
      requestBody =
        suspendProcessInstancesBatchOperationRequestBodySchema.parse(body);
    });
    mockSuspendProcessInstancesBatchOperation().withSuccess(
      {
        batchOperationKey: 'suspend-operation',
        batchOperationType: 'SUSPEND_PROCESS_INSTANCE',
      },
      {requestBodyResolverFn: requestBodyResolver},
    );

    const {user} = render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}?active=true&suspended=true`),
    });

    const activeRow = await screen.findByRole('row', {
      name: /active-instance/i,
    });
    await user.click(
      within(activeRow).getByRole('checkbox', {name: /select row/i}),
    );
    await user.click(
      within(screen.getByRole('row', {name: /suspended-instance/i})).getByRole(
        'checkbox',
        {name: /select row/i},
      ),
    );
    await user.click(
      within(
        screen.getByRole('row', {name: /active-child-instance/i}),
      ).getByRole('checkbox', {name: /select row/i}),
    );

    expect(screen.getByRole('button', {name: 'Suspend'})).toBeEnabled();
    expect(screen.getByRole('button', {name: 'Resume'})).toBeEnabled();

    await user.click(screen.getByRole('button', {name: 'Suspend'}));

    const dialog = screen.getByRole('dialog', {name: 'Apply operation'});
    expect(
      within(dialog).getByText(
        /3 instances selected for suspend operation.*only active process instances will be suspended/i,
      ),
    ).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', {name: 'Apply'}));

    await waitFor(() => {
      expect(requestBodyResolver).toHaveBeenCalledOnce();
      expect(requestBody?.filter).toMatchObject({
        processInstanceKey: {$in: ['active-instance', 'active-child-instance']},
      });
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith(
        expect.objectContaining({
          kind: 'success',
          title:
            'The batch operation "Suspend Process Instance" has been started',
        }),
      );
      expect(
        screen.queryByRole('button', {name: 'Suspend'}),
      ).not.toBeInTheDocument();
    });
  });

  it('should resume selected suspended process instances', async () => {
    const suspendedInstance = createProcessInstance({
      processInstanceKey: 'suspended-instance',
      processDefinitionName: 'Suspended Process',
      state: 'SUSPENDED',
    });
    const activeInstance = createProcessInstance({
      processInstanceKey: 'active-instance',
      processDefinitionName: 'Active Process',
      state: 'ACTIVE',
    });
    const suspendedChildInstance = createProcessInstance({
      processInstanceKey: 'suspended-child-instance',
      processDefinitionName: 'Suspended Child Process',
      state: 'SUSPENDED',
      parentProcessInstanceKey: 'parent-instance',
    });
    const completedInstance = createProcessInstance({
      processInstanceKey: 'completed-instance',
      processDefinitionName: 'Completed Process',
      state: 'COMPLETED',
    });
    mockSearchProcessInstances().withSuccess(
      searchResult([
        suspendedInstance,
        activeInstance,
        suspendedChildInstance,
        completedInstance,
      ]),
    );
    let requestBody:
      ResumeProcessInstancesBatchOperationRequestBody | undefined;
    const requestBodyResolver = vi.fn((body: unknown) => {
      requestBody =
        resumeProcessInstancesBatchOperationRequestBodySchema.parse(body);
    });
    mockResumeProcessInstancesBatchOperation().withSuccess(
      {
        batchOperationKey: 'resume-operation',
        batchOperationType: 'RESUME_PROCESS_INSTANCE',
      },
      {requestBodyResolverFn: requestBodyResolver},
    );

    const {user} = render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}?active=true&suspended=true`),
    });

    const suspendedRow = await screen.findByRole('row', {
      name: /suspended-instance/i,
    });
    await user.click(
      within(suspendedRow).getByRole('checkbox', {name: /select row/i}),
    );
    await user.click(
      within(screen.getByRole('row', {name: /active-instance/i})).getByRole(
        'checkbox',
        {name: /select row/i},
      ),
    );
    await user.click(
      within(
        screen.getByRole('row', {name: /suspended-child-instance/i}),
      ).getByRole('checkbox', {name: /select row/i}),
    );

    expect(screen.getByRole('button', {name: 'Suspend'})).toBeEnabled();
    expect(screen.getByRole('button', {name: 'Resume'})).toBeEnabled();

    await user.click(screen.getByRole('button', {name: 'Resume'}));

    const dialog = screen.getByRole('dialog', {name: 'Apply operation'});
    expect(
      within(dialog).getByText(
        /3 instances selected for resume operation.*only suspended process instances will be resumed/i,
      ),
    ).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', {name: 'Apply'}));

    await waitFor(() => {
      expect(requestBodyResolver).toHaveBeenCalledOnce();
      expect(requestBody?.filter).toMatchObject({
        processInstanceKey: {
          $in: ['suspended-instance', 'suspended-child-instance'],
        },
      });
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith(
        expect.objectContaining({
          kind: 'success',
          title:
            'The batch operation "Resume Process Instance" has been started',
        }),
      );
      expect(
        screen.queryByRole('button', {name: 'Resume'}),
      ).not.toBeInTheDocument();
    });
  });

  it('should only offer resume when all suspended instances are selected', async () => {
    const suspendedInstance = createProcessInstance({
      processInstanceKey: 'suspended-instance',
      processDefinitionName: 'Suspended Process',
      state: 'SUSPENDED',
      hasIncident: true,
    });
    mockSearchProcessInstances().withSuccess(searchResult([suspendedInstance]));
    let requestBody:
      ResumeProcessInstancesBatchOperationRequestBody | undefined;
    const requestBodyResolver = vi.fn((body: unknown) => {
      requestBody =
        resumeProcessInstancesBatchOperationRequestBodySchema.parse(body);
    });
    mockResumeProcessInstancesBatchOperation().withSuccess(
      {
        batchOperationKey: 'resume-operation',
        batchOperationType: 'RESUME_PROCESS_INSTANCE',
      },
      {requestBodyResolverFn: requestBodyResolver},
    );

    const {user} = render(<ListView />, {
      wrapper: getWrapper(
        `${Paths.processes()}?suspended=true&businessId=eq_order-123___like_order`,
      ),
    });

    await screen.findByRole('row', {name: /suspended-instance/i});
    await user.click(screen.getByRole('checkbox', {name: 'Select all rows'}));

    expect(screen.getByRole('button', {name: 'Resume'})).toBeEnabled();
    expect(screen.getByRole('button', {name: 'Suspend'})).toBeDisabled();
    expect(
      screen.getByRole('button', {
        name: 'Cancel',
        description: /no running process instances selected/i,
      }),
    ).toBeDisabled();
    expect(
      screen.getByRole('button', {
        name: 'Retry',
        description: /no process instances with an incident selected/i,
      }),
    ).toBeDisabled();
    expect(
      screen.getByRole('button', {
        name: 'Delete',
        description: /no finished process instances selected/i,
      }),
    ).toBeDisabled();

    await user.click(screen.getByRole('button', {name: 'Resume'}));
    await user.click(
      within(screen.getByRole('dialog', {name: 'Apply operation'})).getByRole(
        'button',
        {name: 'Apply'},
      ),
    );

    await waitFor(() => {
      expect(requestBodyResolver).toHaveBeenCalledOnce();
      expect(requestBody?.filter).toEqual({
        businessId: {$eq: 'order-123', $like: '*order*'},
        state: {$eq: 'SUSPENDED'},
      });
    });
  });

  it('should offer suspend and resume for child instances in a parent-filtered view', async () => {
    const activeChildInstance = createProcessInstance({
      processInstanceKey: 'active-child-instance',
      processDefinitionName: 'Active Child Process',
      state: 'ACTIVE',
      parentProcessInstanceKey: 'parent-instance',
    });
    const suspendedChildInstance = createProcessInstance({
      processInstanceKey: 'suspended-child-instance',
      processDefinitionName: 'Suspended Child Process',
      state: 'SUSPENDED',
      parentProcessInstanceKey: 'parent-instance',
    });
    mockSearchProcessInstances().withSuccess(
      searchResult([activeChildInstance, suspendedChildInstance]),
    );

    const {user} = render(<ListView />, {
      wrapper: getWrapper(
        `${Paths.processes()}?active=true&suspended=true&parentProcessInstanceKey=parent-instance`,
      ),
    });

    const activeChildRow = await screen.findByRole('row', {
      name: /active-child-instance/i,
    });
    await user.click(
      within(activeChildRow).getByRole('checkbox', {name: /select row/i}),
    );
    await user.click(
      within(
        screen.getByRole('row', {name: /suspended-child-instance/i}),
      ).getByRole('checkbox', {name: /select row/i}),
    );

    expect(
      screen
        .getAllByRole('button', {name: 'Cancel'})
        .some((button) => !button.hasAttribute('disabled')),
    ).toBe(true);
    expect(screen.getByRole('button', {name: 'Suspend'})).toBeEnabled();
    expect(screen.getByRole('button', {name: 'Resume'})).toBeEnabled();
  });

  it('should render title and document title', async () => {
    mockSearchProcessInstances().withSuccess(mockProcessInstances);
    mockSearchProcessInstances().withSuccess(mockProcessInstances);

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
    mockSearchProcessInstances().withSuccess(mockProcessInstances);
    mockSearchProcessInstances().withSuccess(mockProcessInstances);

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

    // Add mocks for navigation and periodic refetch
    mockSearchProcessInstances().withSuccess(mockProcessInstances);
    mockSearchProcessInstances().withSuccess(mockProcessInstances);
    await user.click(screen.getByText(/go to active/i));

    const updatedRow = await screen.findByRole('row', {
      name: /2251799813685594/i,
    });

    const updatedCheckbox = within(updatedRow).getByRole('checkbox');

    expect(updatedCheckbox).not.toBeChecked();
  });

  it('should not reset selected instances when table is sorted', async () => {
    mockSearchProcessInstances().withSuccess(mockProcessInstances);
    mockSearchProcessInstances().withSuccess(mockProcessInstances);

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
    mockSearchProcessInstances().withSuccess(mockProcessInstances);
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
    mockSearchProcessInstances().withSuccess(mockProcessInstances);
    mockSearchProcessInstances().withSuccess(mockProcessInstances);

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
    mockSearchProcessInstances().withSuccess(mockProcessInstances);

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

  it('should redirect to initial processes page if selected process definition does not exist', async () => {
    mockSearchProcessDefinitions().withSuccess(searchResult([]));
    mockSearchProcessDefinitions().withSuccess(searchResult([]));
    mockSearchProcessInstances().withSuccess(searchResult([]));
    mockSearchProcessInstances().withSuccess(searchResult([]));
    mockQueryBatchOperations().withSuccess(searchResult([]));

    const queryString =
      '?active=true&incidents=true&processDefinitionId=non-existing-process&processDefinitionVersion=all';
    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}${queryString}`),
    });

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('search').textContent).toBe(queryString);

    expect(screen.getByTestId('diagram-spinner')).toBeInTheDocument();
    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes/);
    await waitFor(() =>
      expect(screen.getByTestId('search').textContent).toBe(
        '?active=true&incidents=true',
      ),
    );

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'error',
      title: 'Process could not be found',
    });
  });

  it('should hide Operation State column when batch operation key filter is not set', async () => {
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

  it('should show Operation State column when batch operation key filter is set', async () => {
    const queryString =
      '?batchOperationKey=f4be6304-a0e0-4976-b81b-7a07fb4e96e5';

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
    const queryString =
      '?batchOperationKey=f4be6304-a0e0-4976-b81b-7a07fb4e96e5';

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
    const queryString =
      '?batchOperationKey=f4be6304-a0e0-4976-b81b-7a07fb4e96e5';

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

  it('should display "10000+ results" when there are more than 10000 results', async () => {
    const mockLargeProcessInstancesResult: QueryProcessInstancesResponseBody = {
      items: [
        createProcessInstance({
          processInstanceKey: '2251799813685594',
          processDefinitionKey: '2251799813685592',
          processDefinitionId: 'someKey',
          processDefinitionName: 'someProcessName',
          state: 'ACTIVE',
        }),
      ],
      page: {
        totalItems: 10000,
        startCursor: null,
        endCursor: null,
        hasMoreTotalItems: true,
      },
    };

    mockSearchProcessInstances().withSuccess(mockLargeProcessInstancesResult);

    render(<ListView />, {
      wrapper: getWrapper(`${Paths.processes()}?active=true`),
    });

    expect(
      await screen.findByRole('heading', {
        name: /process instances - 10000\+ results/i,
      }),
    ).toBeInTheDocument();
  });
});
