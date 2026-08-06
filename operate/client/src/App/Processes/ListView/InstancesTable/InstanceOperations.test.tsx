/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render, screen, waitFor} from 'modules/testing-library';
import {InstanceOperations} from './InstanceOperations';
import {createProcessInstance} from 'modules/testUtils';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter} from 'react-router-dom';
import {notificationsStore} from 'modules/stores/notifications';
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

const rootActiveInstance = createProcessInstance({
  processInstanceKey: '123456789',
  state: 'ACTIVE',
  hasIncident: false,
  parentProcessInstanceKey: null,
});

describe('InstanceOperations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render suspend for an active root instance', () => {
    render(
      <InstanceOperations
        processInstance={rootActiveInstance}
        activeOperations={[]}
      />,
      {wrapper: getWrapper()},
    );

    expect(
      screen.getByRole('button', {name: /Suspend Instance/}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /Cancel Instance/}),
    ).toBeInTheDocument();
  });

  it('should not render suspend for an active non-root instance', () => {
    const childInstance = createProcessInstance({
      state: 'ACTIVE',
      parentProcessInstanceKey: '111111111',
    });

    render(
      <InstanceOperations
        processInstance={childInstance}
        activeOperations={[]}
      />,
      {wrapper: getWrapper()},
    );

    expect(
      screen.queryByRole('button', {name: /Suspend Instance/}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /Cancel Instance/}),
    ).toBeInTheDocument();
  });

  it('should render resume and cancel, but not delete, for a suspended root instance', () => {
    const suspendedInstance = createProcessInstance({
      state: 'SUSPENDED',
      parentProcessInstanceKey: null,
    });

    render(
      <InstanceOperations
        processInstance={suspendedInstance}
        activeOperations={[]}
      />,
      {wrapper: getWrapper()},
    );

    expect(
      screen.getByRole('button', {name: /Resume Instance/}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /Cancel Instance/}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /Suspend Instance/}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /Delete Instance/}),
    ).not.toBeInTheDocument();
  });

  it('should render cancel but not resume for a suspended non-root instance', () => {
    const suspendedChildInstance = createProcessInstance({
      state: 'SUSPENDED',
      parentProcessInstanceKey: '111111111',
    });

    render(
      <InstanceOperations
        processInstance={suspendedChildInstance}
        activeOperations={[]}
      />,
      {wrapper: getWrapper()},
    );

    expect(
      screen.getByRole('button', {name: /Cancel Instance/}),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /Resume Instance/}),
    ).not.toBeInTheDocument();
  });

  it('should render delete for a completed instance', () => {
    const completedInstance = createProcessInstance({state: 'COMPLETED'});

    render(
      <InstanceOperations
        processInstance={completedInstance}
        activeOperations={[]}
      />,
      {wrapper: getWrapper()},
    );

    expect(
      screen.getByRole('button', {name: /Delete Instance/}),
    ).toBeInTheDocument();
  });

  it('should show error notification on suspend error', async () => {
    mockSuspendProcessInstance().withServerError();

    const {user} = render(
      <InstanceOperations
        processInstance={rootActiveInstance}
        activeOperations={[]}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /Suspend Instance/}));

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Failed to suspend process instance',
      subtitle: 'Internal Server Error',
      isDismissable: true,
    });
  });

  it('should show success notification on suspend success', async () => {
    mockSuspendProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess({
      ...rootActiveInstance,
      state: 'SUSPENDED',
    });

    const {user} = render(
      <InstanceOperations
        processInstance={rootActiveInstance}
        activeOperations={[]}
      />,
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

  it('should show error notification on resume error', async () => {
    const suspendedInstance = createProcessInstance({
      state: 'SUSPENDED',
      parentProcessInstanceKey: null,
    });
    mockResumeProcessInstance().withServerError();

    const {user} = render(
      <InstanceOperations
        processInstance={suspendedInstance}
        activeOperations={[]}
      />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByRole('button', {name: /Resume Instance/}));

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'Failed to resume process instance',
      subtitle: 'Internal Server Error',
      isDismissable: true,
    });
  });

  it('should show success notification on resume success', async () => {
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
      <InstanceOperations
        processInstance={suspendedInstance}
        activeOperations={[]}
      />,
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
  });

  it('should disable suspend while a suspend operation is already active', () => {
    render(
      <InstanceOperations
        processInstance={rootActiveInstance}
        activeOperations={['SUSPEND_PROCESS_INSTANCE']}
      />,
      {wrapper: getWrapper()},
    );

    expect(
      screen.getByRole('button', {name: /Suspend Instance/}),
    ).toBeDisabled();
  });
});
