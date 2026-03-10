/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {ProcessInstanceOperations} from './ProcessInstanceOperations';
import {createProcessInstance} from 'modules/testUtils';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter} from 'react-router-dom';
import {modificationsStore} from 'modules/stores/modifications';
import {notificationsStore} from 'modules/stores/notifications';
import {mockCancelProcessInstance} from 'modules/mocks/api/v2/processInstances/cancelProcessInstance';
import {mockResolveProcessInstanceIncidents} from 'modules/mocks/api/v2/processInstances/resolveProcessInstanceIncidents';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';
import {mockDeleteProcessInstance} from 'modules/mocks/api/v2/processInstances/deleteProcessInstance';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {IS_NEW_PROCESS_INSTANCE_PAGE} from 'modules/feature-flags';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const getWrapper = () => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <MemoryRouter>
        <QueryClientProvider client={getMockQueryClient()}>
          {children}
        </QueryClientProvider>
      </MemoryRouter>
    );
  };
  return Wrapper;
};

const mockProcessInstance = createProcessInstance({
  processInstanceKey: '123456789',
  state: 'ACTIVE',
  hasIncident: false,
  parentProcessInstanceKey: null,
  parentElementInstanceKey: null,
  rootProcessInstanceKey: null,
  tags: [],
});

describe('ProcessInstanceOperations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    modificationsStore.reset();
    mockFetchCallHierarchy().withSuccess([]);
  });

  it('should render operations for active instance with incident', () => {
    const instanceWithIncident = createProcessInstance({
      state: 'ACTIVE',
      hasIncident: true,
      parentProcessInstanceKey: null,
      parentElementInstanceKey: null,
      rootProcessInstanceKey: null,
      tags: [],
    });

    render(
      <ProcessInstanceOperations processInstance={instanceWithIncident} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      screen.getByRole('button', {name: /Retry Instance/}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /Cancel Instance/}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /Modify Instance/}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /Migrate Instance/}),
    ).toBeInTheDocument();
  });

  it('should render operations for active instance without incident', () => {
    render(
      <ProcessInstanceOperations processInstance={mockProcessInstance} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      screen.queryByRole('button', {name: /Retry Instance/}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /Cancel Instance/}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /Modify Instance/}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /Migrate Instance/}),
    ).toBeInTheDocument();
  });

  it('should render delete operation for terminated instance', () => {
    const terminatedInstance = createProcessInstance({
      state: 'TERMINATED',
    });

    render(<ProcessInstanceOperations processInstance={terminatedInstance} />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.getByRole('button', {name: /Delete Instance/}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /Cancel Instance/}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /Modify Instance/}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /Migrate Instance/}),
    ).not.toBeInTheDocument();
  });

  it('should hide operations when modification mode is enabled', () => {
    modificationsStore.enableModificationMode();
    const instanceWithIncident = createProcessInstance({
      state: 'ACTIVE',
      hasIncident: true,
      parentProcessInstanceKey: null,
      parentElementInstanceKey: null,
      rootProcessInstanceKey: null,
      tags: [],
    });

    render(
      <ProcessInstanceOperations processInstance={instanceWithIncident} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      screen.queryByRole('button', {name: /Retry Instance/}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /Cancel Instance/}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /Modify Instance/}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /Migrate Instance/}),
    ).not.toBeInTheDocument();
  });

  it('should show error notification on cancel error', async () => {
    mockCancelProcessInstance().withServerError();

    const {user} = render(
      <ProcessInstanceOperations processInstance={mockProcessInstance} />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /Cancel Instance/}));
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Failed to cancel process instance',
        subtitle: 'Internal Server Error',
        isDismissable: true,
      }),
    );
  });

  it(
    'should show success notification on cancel success',
    {skip: !IS_NEW_PROCESS_INSTANCE_PAGE},
    async () => {
      mockCancelProcessInstance().withSuccess({});

      const {user} = render(
        <ProcessInstanceOperations processInstance={mockProcessInstance} />,
        {wrapper: getWrapper()},
      );

      await user.click(screen.getByRole('button', {name: /Cancel Instance/}));
      await user.click(screen.getByRole('button', {name: 'Apply'}));

      await waitFor(() =>
        expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
          kind: 'success',
          title: 'Instance is scheduled for cancellation',
          isDismissable: true,
        }),
      );
    },
  );

  it('should show error notification on resolve incident error', async () => {
    mockResolveProcessInstanceIncidents().withServerError();
    const instanceWithIncident = createProcessInstance({
      state: 'ACTIVE',
      hasIncident: true,
      parentProcessInstanceKey: null,
      parentElementInstanceKey: null,
      rootProcessInstanceKey: null,
      tags: [],
    });

    const {user} = render(
      <ProcessInstanceOperations processInstance={instanceWithIncident} />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /Retry Instance/}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Operation could not be created',
        isDismissable: true,
      }),
    );
  });

  it(
    'should show success notification on resolve incident success',
    {skip: !IS_NEW_PROCESS_INSTANCE_PAGE},
    async () => {
      mockResolveProcessInstanceIncidents().withSuccess({});
      const instanceWithIncident = createProcessInstance({
        state: 'ACTIVE',
        hasIncident: true,
        parentProcessInstanceKey: null,
        parentElementInstanceKey: null,
        rootProcessInstanceKey: null,
        tags: [],
      });

      const {user} = render(
        <ProcessInstanceOperations processInstance={instanceWithIncident} />,
        {wrapper: getWrapper()},
      );

      await user.click(screen.getByRole('button', {name: /Retry Instance/}));

      await waitFor(() =>
        expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
          kind: 'success',
          title: 'Incidents are scheduled for retry',
          isDismissable: true,
        }),
      );
    },
  );

  it('should show error notification on resolve incident permission error', async () => {
    mockResolveProcessInstanceIncidents().withServerError(403);
    const instanceWithIncident = createProcessInstance({
      state: 'ACTIVE',
      hasIncident: true,
      parentProcessInstanceKey: null,
      parentElementInstanceKey: null,
      rootProcessInstanceKey: null,
      tags: [],
    });

    const {user} = render(
      <ProcessInstanceOperations processInstance={instanceWithIncident} />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /Retry Instance/}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'warning',
        title: "You don't have permission to perform this operation",
        subtitle: 'Please contact the administrator if you need access.',
        isDismissable: true,
      }),
    );
  });

  it('should show error notification on delete operation error', async () => {
    mockDeleteProcessInstance().withServerError();
    const terminatedInstance = createProcessInstance({
      state: 'TERMINATED',
    });

    const {user} = render(
      <ProcessInstanceOperations processInstance={terminatedInstance} />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /Delete Instance/}));
    await user.click(screen.getByRole('button', {name: 'danger Delete'}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Failed to delete process instance',
        subtitle: 'Internal Server Error',
        isDismissable: true,
      }),
    );
  });

  it('should show success notification on delete operation success', async () => {
    mockDeleteProcessInstance().withSuccess({});
    const terminatedInstance = createProcessInstance({
      state: 'TERMINATED',
    });

    const {user} = render(
      <ProcessInstanceOperations processInstance={terminatedInstance} />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /Delete Instance/}));
    await user.click(screen.getByRole('button', {name: 'danger Delete'}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'success',
        title: 'Instance is scheduled for deletion',
        isDismissable: true,
      }),
    );
  });

  // TODO: This test might be obsolete depending on the UX alignment. Do we want
  // to continue reporting any running batch operation in the header? Not intended
  // in the original prototype. https://github.com/camunda/camunda/issues/46750
  it.todo(
    'should show spinner when process instance has active operation items',
    async () => {
      mockQueryBatchOperationItems().withSuccess({
        items: [
          {
            batchOperationKey: 'batch-123',
            processInstanceKey: '123456789',
            state: 'ACTIVE',
            operationType: 'CANCEL_PROCESS_INSTANCE',
            itemKey: '123456789',
            rootProcessInstanceKey: null,
            processedDate: null,
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

      render(
        <ProcessInstanceOperations processInstance={mockProcessInstance} />,
        {wrapper: getWrapper()},
      );

      await waitFor(() => {
        expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
      });
    },
  );
});
