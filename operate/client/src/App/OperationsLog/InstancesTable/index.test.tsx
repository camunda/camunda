/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {InstancesTable} from './index';
import {render, screen, waitFor} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {mockQueryAuditLogs} from 'modules/mocks/api/v2/auditLogs/queryAuditLogs';
import {notificationsStore} from 'modules/stores/notifications';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

vi.mock('modules/tracking', () => ({
  tracking: {
    track: vi.fn(),
  },
}));

const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={['/operations-log']}>
        <Routes>
          <Route path="/operations-log" element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

describe('OperationsLog InstancesTable', () => {
  beforeEach(() => {
    mockSearchProcessDefinitions().withSuccess({
      items: [
        {
          processDefinitionKey: '123456',
          processDefinitionId: 'process1',
          name: 'Test Process',
          version: 1,
          tenantId: '<default>',
          hasStartForm: false,
        },
      ],
      page: {totalItems: 1},
    });
  });

  it('should render operations log header', () => {
    mockQueryAuditLogs().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    expect(screen.getByText('Operations Log')).toBeInTheDocument();
  });

  it('should show loading state when data is being fetched', () => {
    mockQueryAuditLogs().withDelay({
      items: [],
      page: {totalItems: 0},
    });

    render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTestId('data-table-loader')).toBeInTheDocument();
  });

  it('should render empty state when no operations are found', async () => {
    mockQueryAuditLogs().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    expect(
      await screen.findByText('No operations log found'),
    ).toBeInTheDocument();
    expect(
      await screen.findByText(
        'Try adjusting your filters or check back later.',
      ),
    ).toBeInTheDocument();
  });

  it('should render table headers correctly', async () => {
    mockQueryAuditLogs().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    expect(
      screen.getByRole('columnheader', {name: /operation type/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /entity type/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /reference to entity/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /property/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /actor/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /date/i}),
    ).toBeInTheDocument();
  });

  it('should render audit log rows when data is present', async () => {
    mockQueryAuditLogs().withSuccess({
      items: [
        {
          auditLogKey: '123',
          entityKey: '1',
          operationType: 'UPDATE',
          entityType: 'VARIABLE',
          result: 'SUCCESS',
          actorId: 'user1',
          timestamp: '2024-01-01T00:00:00.000Z',
          annotation: 'Updated variable',
          actorType: 'USER',
          category: 'USER_TASKS',
          entityDescription: 'variableName',
        },
        {
          auditLogKey: '456',
          entityKey: '2',
          operationType: 'CREATE',
          entityType: 'RESOURCE',
          result: 'FAIL',
          actorId: 'user2',
          timestamp: '2024-01-02T00:00:00.000Z',
          annotation: 'Created instance',
          actorType: 'USER',
          category: 'DEPLOYED_RESOURCES',
          entityDescription: 'ERROR_CODE',
        },
      ],
      page: {totalItems: 2},
    });

    render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Update')).toBeInTheDocument();
    expect(await screen.findByText('Variable')).toBeInTheDocument();
    expect(await screen.findByText('Create')).toBeInTheDocument();
    expect(await screen.findByText('Resource')).toBeInTheDocument();
    expect(screen.getByTestId('SUCCESS-icon')).toBeInTheDocument();
    expect(screen.getByTestId('FAIL-icon')).toBeInTheDocument();
    expect(screen.getAllByText('user1').at(0)).toBeInTheDocument();
    expect(screen.getAllByText('user2').at(0)).toBeInTheDocument();
    expect(screen.getByText(/variable name/i)).toBeInTheDocument();
    expect(await screen.findByText(/error code/i)).toBeInTheDocument();
    expect(screen.getByText('variableName')).toBeInTheDocument();
    expect(screen.getByText('ERROR_CODE')).toBeInTheDocument();
  });

  it('should render batch operation information', async () => {
    mockQueryAuditLogs().withSuccess({
      items: [
        {
          auditLogKey: '789',
          entityKey: '3',
          operationType: 'CANCEL',
          entityType: 'BATCH',
          batchOperationType: 'CANCEL_PROCESS_INSTANCE',
          batchOperationKey: 'batch-123',
          result: 'SUCCESS',
          actorId: 'admin',
          timestamp: '2024-01-03T00:00:00.000Z',
          annotation: 'Batch operation',
          actorType: 'USER',
          category: 'DEPLOYED_RESOURCES',
        },
      ],
      page: {totalItems: 1},
    });

    render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText('Cancel')).toBeInTheDocument();
    expect(screen.getByText('Batch')).toBeInTheDocument();
    expect(screen.getByText(/batch operation type/i)).toBeInTheDocument();
    expect(screen.getByText(/cancel process instance/i)).toBeInTheDocument();
    expect(screen.getByText(/multiple process instances/i)).toBeInTheDocument();
    expect(screen.getByText('batch-123')).toHaveAttribute(
      'href',
      '/batch-operations/batch-123',
    );
  });

  it('should open details modal when info button is clicked', async () => {
    mockQueryAuditLogs().withSuccess({
      items: [
        {
          auditLogKey: '123',
          entityKey: '1',
          operationType: 'UPDATE',
          entityType: 'VARIABLE',
          result: 'SUCCESS',
          actorId: 'user1',
          timestamp: '2024-01-01T00:00:00.000Z',
          annotation: 'Updated variable',
          actorType: 'USER',
          category: 'USER_TASKS',
        },
      ],
      page: {totalItems: 1},
    });

    const {user} = render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    const detailsButton = await screen.findByRole('button', {
      name: /open details/i,
    });

    await user.click(detailsButton);

    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('should handle error state', async () => {
    mockQueryAuditLogs().withNetworkError();

    render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        isDismissable: true,
        kind: 'error',
        title: 'Audit logs could not be fetched',
      }),
    );
  });

  it('should format timestamps correctly', async () => {
    mockQueryAuditLogs().withSuccess({
      items: [
        {
          auditLogKey: '123',
          entityKey: '1',
          operationType: 'UPDATE',
          entityType: 'VARIABLE',
          result: 'SUCCESS',
          actorId: 'user1',
          timestamp: '2024-01-01T12:30:45.000Z',
          annotation: 'Test',
          actorType: 'USER',
          category: 'USER_TASKS',
        },
      ],
      page: {totalItems: 1},
    });

    render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText(/2024-01-01 12:30:45/)).toBeInTheDocument();
  });

  it('should render process instance link for PROCESS_INSTANCE entity type', async () => {
    mockQueryAuditLogs().withSuccess({
      items: [
        {
          auditLogKey: '123',
          entityKey: '999',
          processInstanceKey: '999',
          operationType: 'CANCEL',
          entityType: 'PROCESS_INSTANCE',
          result: 'SUCCESS',
          actorId: 'user1',
          timestamp: '2024-01-01T00:00:00.000Z',
          annotation: 'Cancelled',
          actorType: 'USER',
          category: 'DEPLOYED_RESOURCES',
          processDefinitionId: 'process1',
          processDefinitionKey: '123456',
        },
      ],
      page: {totalItems: 1},
    });

    render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    expect(
      await screen.findByRole('link', {name: 'View process instance 999'}),
    ).toBeInTheDocument();
    expect(await screen.findByText('Test Process')).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'View process instance 999'}),
    ).toHaveAttribute('href', '/processes/999');
  });

  it('should render decision instance link for DECISION entity type', async () => {
    mockQueryAuditLogs().withSuccess({
      items: [
        {
          auditLogKey: '123',
          entityKey: '888',
          operationType: 'EVALUATE',
          entityType: 'DECISION',
          result: 'SUCCESS',
          actorId: 'user1',
          timestamp: '2024-01-01T00:00:00.000Z',
          annotation: 'Evaluated',
          actorType: 'USER',
          category: 'DEPLOYED_RESOURCES',
        },
      ],
      page: {totalItems: 1},
    });

    render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    expect(
      await screen.findByRole('link', {name: 'View decision 888'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {name: 'View decision 888'}),
    ).toHaveAttribute('href', '/decisions/888');
  });
});
