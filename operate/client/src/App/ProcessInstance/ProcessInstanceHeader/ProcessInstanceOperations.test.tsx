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
import {mockSuspendProcessInstance} from 'modules/mocks/api/v2/processInstances/suspendProcessInstance';
import {mockResumeProcessInstance} from 'modules/mocks/api/v2/processInstances/resumeProcessInstance';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';

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

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Failed to cancel process instance',
      subtitle: 'Internal Server Error',
      isDismissable: true,
    });
  });

  it('should show success notification on cancel success', async () => {
    mockCancelProcessInstance().withSuccess({});

    const {user} = render(
      <ProcessInstanceOperations processInstance={mockProcessInstance} />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /Cancel Instance/}));
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'info',
      title: 'Instance is scheduled for cancellation',
      isDismissable: true,
    });
  });

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

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Operation could not be created',
      isDismissable: true,
    });
  });

  it('should show success notification on resolve incident success', async () => {
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

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'info',
      title: 'Incidents are scheduled for retry',
      isDismissable: true,
    });
  });

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

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'warning',
      title: "You don't have permission to perform this operation",
      subtitle: 'Please contact the administrator if you need access.',
      isDismissable: true,
    });
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
    await user.click(screen.getByRole('button', {name: /^delete$/i}));

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Failed to delete process instance',
      subtitle: 'Internal Server Error',
      isDismissable: true,
    });
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
    await user.click(screen.getByRole('button', {name: /^delete$/i}));

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'info',
      title: 'Instance is scheduled for deletion',
      isDismissable: true,
    });
  });

  it('should suspend an active root instance', async () => {
    mockSuspendProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess({
      ...mockProcessInstance,
      state: 'SUSPENDED',
    });

    const {user} = render(
      <ProcessInstanceOperations processInstance={mockProcessInstance} />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /Suspend Instance/}));

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'info',
        title: 'Instance suspended',
        isDismissable: true,
      });
    });
  });

  it('should allow suspending an active child instance independently of its parent', async () => {
    const activeChildInstance = createProcessInstance({
      state: 'ACTIVE',
      parentProcessInstanceKey: '111111111',
    });
    mockSuspendProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess({
      ...activeChildInstance,
      state: 'SUSPENDED',
    });

    const {user} = render(
      <ProcessInstanceOperations processInstance={activeChildInstance} />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /Suspend Instance/}));

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'info',
        title: 'Instance suspended',
        isDismissable: true,
      });
    });
    expect(
      screen.getByRole('button', {name: /Cancel Instance/}),
    ).toBeInTheDocument();
  });

  it('should resume a suspended root instance', async () => {
    const suspendedInstance = createProcessInstance({
      state: 'SUSPENDED',
      parentProcessInstanceKey: null,
    });
    mockResumeProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess({
      ...suspendedInstance,
      state: 'ACTIVE',
    });

    const {user} = render(
      <ProcessInstanceOperations processInstance={suspendedInstance} />,
      {wrapper: getWrapper()},
    );

    expect(
      screen.getByRole('button', {name: /Cancel Instance/}),
    ).toBeInTheDocument();
    await user.click(screen.getByRole('button', {name: /Resume Instance/}));

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'info',
        title: 'Instance resumed',
        isDismissable: true,
      });
    });
  });

  it('should allow resuming a suspended child instance independently of its parent', async () => {
    const suspendedChildInstance = createProcessInstance({
      state: 'SUSPENDED',
      parentProcessInstanceKey: '111111111',
    });
    mockResumeProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess({
      ...suspendedChildInstance,
      state: 'ACTIVE',
    });

    const {user} = render(
      <ProcessInstanceOperations processInstance={suspendedChildInstance} />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /Resume Instance/}));

    await waitFor(() => {
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'info',
        title: 'Instance resumed',
        isDismissable: true,
      });
    });
    expect(
      screen.getByRole('button', {name: /Cancel Instance/}),
    ).toBeInTheDocument();
  });
});
