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
import {processInstancesStore} from 'modules/stores/processInstances';
import {mockCancelProcessInstance} from 'modules/mocks/api/v2/processInstances/cancelProcessInstance';
import {mockCreateIncidentResolutionBatchOperation} from 'modules/mocks/api/v2/processInstances/createIncidentResolutionBatchOperation';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';

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
});

describe('ProcessInstanceOperations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    modificationsStore.reset();
    processInstancesStore.reset();
    mockFetchCallHierarchy().withSuccess([]);
  });

  it('should render operations for active instance with incident', () => {
    const instanceWithIncident = createProcessInstance({
      state: 'ACTIVE',
      hasIncident: true,
    });

    render(
      <ProcessInstanceOperations processInstance={instanceWithIncident} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByTitle(/retry instance/i)).toBeInTheDocument();
    expect(screen.getByTitle(/cancel instance/i)).toBeInTheDocument();
    expect(screen.getByTitle(/modify instance/i)).toBeInTheDocument();
  });

  it('should render operations for active instance without incident', () => {
    render(
      <ProcessInstanceOperations processInstance={mockProcessInstance} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.queryByTitle(/retry instance/i)).not.toBeInTheDocument();
    expect(screen.getByTitle(/cancel instance/i)).toBeInTheDocument();
    expect(screen.getByTitle(/modify instance/i)).toBeInTheDocument();
  });

  it('should render delete operation for terminated instance', () => {
    const terminatedInstance = createProcessInstance({
      state: 'TERMINATED',
    });

    render(<ProcessInstanceOperations processInstance={terminatedInstance} />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTitle(/delete instance/i)).toBeInTheDocument();
    expect(screen.queryByTitle(/cancel instance/i)).not.toBeInTheDocument();
    expect(screen.queryByTitle(/modify instance/i)).not.toBeInTheDocument();
  });

  it('should hide operations when modification mode is enabled', () => {
    modificationsStore.enableModificationMode();
    const instanceWithIncident = createProcessInstance({
      state: 'ACTIVE',
      hasIncident: true,
    });

    render(
      <ProcessInstanceOperations processInstance={instanceWithIncident} />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.queryByTitle(/retry instance/i)).not.toBeInTheDocument();
    expect(screen.queryByTitle(/cancel instance/i)).not.toBeInTheDocument();
    expect(screen.queryByTitle(/modify instance/i)).not.toBeInTheDocument();
  });

  it('should show notification on cancel error', async () => {
    mockCancelProcessInstance().withServerError();

    const {user} = render(
      <ProcessInstanceOperations processInstance={mockProcessInstance} />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByTitle(/cancel instance/i));
    await user.click(screen.getByRole('button', {name: /apply/i}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Failed to cancel process instance',
        subtitle: 'Internal Server Error',
        isDismissable: true,
      }),
    );
  });

  it('should show notification on resolve incident error', async () => {
    mockCreateIncidentResolutionBatchOperation().withServerError();
    const instanceWithIncident = createProcessInstance({
      state: 'ACTIVE',
      hasIncident: true,
    });

    const {user} = render(
      <ProcessInstanceOperations processInstance={instanceWithIncident} />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByTitle(/retry instance/i));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Failed to retry process instance',
        subtitle: 'Internal Server Error',
        isDismissable: true,
      }),
    );
  });

  it('should show notification on delete operation error', async () => {
    mockApplyOperation().withServerError();
    const terminatedInstance = createProcessInstance({
      state: 'TERMINATED',
    });

    const {user} = render(
      <ProcessInstanceOperations processInstance={terminatedInstance} />,
      {wrapper: getWrapper()},
    );

    await user.click(screen.getByTitle(/delete instance/i));
    const modal = screen.getByTestId('confirm-deletion-modal');
    const deleteButton = modal.querySelector('button[class*="btn--danger"]')!;
    await user.click(deleteButton);

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        kind: 'error',
        title: 'Operation could not be created',
        isDismissable: true,
      }),
    );
  });
});
