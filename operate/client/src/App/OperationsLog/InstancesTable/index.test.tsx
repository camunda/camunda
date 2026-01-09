/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect, vi, beforeEach, afterEach} from 'vitest';
import {InstancesTable} from './index';
import {render, screen, waitFor} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import React from 'react';
import {mockQueryAuditLogs} from 'modules/mocks/api/v2/auditLogs/queryAuditLogs';
import {notificationsStore} from 'modules/stores/notifications';
import {processesStore} from 'modules/stores/processes/processes.list';
import {mockSearchProcessDefinitions} from 'modules/mocks/api/v2/processDefinitions/searchProcessDefinitions';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';

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

const Wrapper = ({
  children,
  initialPath = '/operations-log',
  processDefinitionKey = null,
}: {
  children?: React.ReactNode;
  initialPath?: string;
  processDefinitionKey?: string | null;
}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route
            path="/operations-log"
            element={
              <ProcessDefinitionKeyContext.Provider
                value={processDefinitionKey ?? undefined}
              >
                {children}
              </ProcessDefinitionKeyContext.Provider>
            }
          />
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
          processDefinitionKey: '123',
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

  afterEach(() => {
    processesStore.reset();
    vi.clearAllMocks();
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

    await waitFor(() => {
      expect(
        screen.getByRole('columnheader', {name: /operation/i}),
      ).toBeInTheDocument();
    });

    expect(
      screen.getByRole('columnheader', {name: /entity/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /status/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /applied to/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /actor/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /time/i}),
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
          category: 'USER_TASK',
        },
        {
          auditLogKey: '456',
          entityKey: '2',
          operationType: 'CREATE',
          entityType: 'PROCESS_INSTANCE',
          result: 'FAIL',
          actorId: 'user2',
          timestamp: '2024-01-02T00:00:00.000Z',
          annotation: 'Created instance',
          actorType: 'USER',
          category: 'OPERATOR',
        },
      ],
      page: {totalItems: 2},
    });

    render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText(/update variable/i)).toBeInTheDocument();
    expect(screen.getByText(/create process instance/i)).toBeInTheDocument();
    expect(screen.getByText(/success/i)).toBeInTheDocument();
    expect(screen.getByText(/fail/i)).toBeInTheDocument();
    expect(screen.getByText('user1')).toBeInTheDocument();
    expect(screen.getByText('user2')).toBeInTheDocument();
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
          category: 'OPERATOR',
        },
      ],
      page: {totalItems: 1},
    });

    render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    expect(await screen.findByText(/cancel batch/i)).toBeInTheDocument();
    expect(screen.getByText(/cancel process instance/i)).toBeInTheDocument();
    expect(screen.getByText('batch-123')).toBeInTheDocument();
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
          category: 'USER_TASK',
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
          category: 'USER_TASK',
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
          operationType: 'CANCEL',
          entityType: 'PROCESS_INSTANCE',
          result: 'SUCCESS',
          actorId: 'user1',
          timestamp: '2024-01-01T00:00:00.000Z',
          annotation: 'Cancelled',
          actorType: 'USER',
          category: 'OPERATOR',
          processDefinitionId: 'process1',
        },
      ],
      page: {totalItems: 1},
    });

    render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    await waitFor(() => {
      const link = screen.getByRole('link', {name: '999'});
      expect(link).toHaveAttribute('href', '/processes/999');
    });
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
          category: 'OPERATOR',
        },
      ],
      page: {totalItems: 1},
    });

    render(<InstancesTable />, {
      wrapper: Wrapper,
    });

    await waitFor(() => {
      const link = screen.getByRole('link', {name: '888'});
      expect(link).toHaveAttribute('href', '/decisions/888');
    });
  });
});
