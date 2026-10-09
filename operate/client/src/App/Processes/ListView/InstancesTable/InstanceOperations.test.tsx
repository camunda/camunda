/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockResumeProcessInstance} from 'modules/mocks/api/v2/processInstances/resumeProcessInstance';
import {mockSuspendProcessInstance} from 'modules/mocks/api/v2/processInstances/suspendProcessInstance';
import {notificationsStore} from 'modules/stores/notifications';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {render, screen, waitFor} from 'modules/testing-library';
import {createProcessInstance} from 'modules/testUtils';
import {InstanceOperations} from './InstanceOperations';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const getWrapper = () => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
    <MemoryRouter>
      <QueryClientProvider client={getMockQueryClient()}>
        {children}
      </QueryClientProvider>
    </MemoryRouter>
  );
  return Wrapper;
};

describe('InstanceOperations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should suspend an active root instance', async () => {
    const activeInstance = createProcessInstance({
      state: 'ACTIVE',
      parentProcessInstanceKey: null,
    });
    mockSuspendProcessInstance().withSuccess(null);
    mockFetchProcessInstance().withSuccess({
      ...activeInstance,
      state: 'SUSPENDED',
    });

    const {user} = render(
      <InstanceOperations
        processInstance={activeInstance}
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
      <InstanceOperations
        processInstance={activeChildInstance}
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
    expect(
      screen.getByRole('button', {name: /Cancel Instance/}),
    ).toBeInTheDocument();
  });

  it('should preserve action positions when retry is unavailable', () => {
    const activeInstance = createProcessInstance({
      state: 'ACTIVE',
      hasIncident: false,
      parentProcessInstanceKey: null,
    });

    render(
      <InstanceOperations
        processInstance={activeInstance}
        activeOperations={[]}
      />,
      {wrapper: getWrapper()},
    );

    const suspendButton = screen.getByRole('button', {
      name: /Suspend Instance/,
    });
    const cancelButton = screen.getByRole('button', {name: /Cancel Instance/});
    const operationSlots = screen.getAllByRole('listitem', {hidden: true});

    expect(operationSlots).toHaveLength(4);
    expect(operationSlots[0]).toHaveAttribute('aria-hidden', 'true');
    expect(operationSlots[1]).toHaveAttribute('aria-hidden', 'true');
    expect(operationSlots[2]).toContainElement(suspendButton);
    expect(operationSlots[3]).toContainElement(cancelButton);
  });

  it('should preserve action positions while an operation is loading', () => {
    const activeInstance = createProcessInstance({
      state: 'ACTIVE',
      hasIncident: false,
      parentProcessInstanceKey: null,
    });

    render(
      <InstanceOperations
        processInstance={activeInstance}
        activeOperations={['RESOLVE_INCIDENT']}
      />,
      {wrapper: getWrapper()},
    );

    const loadingIndicator = screen.getByRole('img', {
      name: /has scheduled Operations/,
    });
    const suspendButton = screen.getByRole('button', {
      name: /Suspend Instance/,
    });
    const cancelButton = screen.getByRole('button', {name: /Cancel Instance/});
    const operationSlots = screen.getAllByRole('listitem', {hidden: true});

    expect(operationSlots[0]).toContainElement(loadingIndicator);
    expect(operationSlots[1]).toHaveAttribute('aria-hidden', 'true');
    expect(operationSlots[2]).toContainElement(suspendButton);
    expect(operationSlots[3]).toContainElement(cancelButton);
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
      <InstanceOperations
        processInstance={suspendedInstance}
        activeOperations={[]}
      />,
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
      <InstanceOperations
        processInstance={suspendedChildInstance}
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
    expect(
      screen.getByRole('button', {name: /Cancel Instance/}),
    ).toBeInTheDocument();
  });
});
