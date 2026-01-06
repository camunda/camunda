/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect, vi} from 'vitest';
import {OperationsLog} from './index';
import {render, screen, waitFor} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter} from 'react-router-dom';
import React from 'react';
import {mockQueryAuditLogs} from 'modules/mocks/api/v2/auditLogs/queryAuditLogs';
import {notificationsStore} from 'modules/stores/notifications';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
};

describe('OperationsLog', () => {
  it('should show skeleton state when data undefined', () => {
    mockQueryAuditLogs().withServerError();

    render(
      <OperationsLog
        isRootNodeSelected={true}
        flowNodeInstanceId="123"
        isVisible={false}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();
  });

  it('should render empty state when no items are present', async () => {
    mockQueryAuditLogs().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    render(
      <OperationsLog
        isRootNodeSelected={true}
        flowNodeInstanceId="123"
        isVisible={true}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      await screen.findByText('No operations found for this instance'),
    ).toBeInTheDocument();
  });

  it('should render rows when data is present', async () => {
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

    render(
      <OperationsLog
        isRootNodeSelected={true}
        flowNodeInstanceId="123"
        isVisible={true}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(
      screen.getByRole('columnheader', {name: /operation/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /status/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /actor/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /time/i}),
    ).toBeInTheDocument();

    expect(await screen.findByText(/update variable/i)).toBeInTheDocument();
    expect(screen.getByText(/success/i)).toBeInTheDocument();
    expect(screen.getByText('user1')).toBeInTheDocument();
    expect(screen.getByText('2024-01-01 00:00:00')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /open details/i}),
    ).toBeInTheDocument();
  });

  it('should handle loading state', () => {
    mockQueryAuditLogs().withDelay({
      items: [],
      page: {totalItems: 0},
    });

    render(
      <OperationsLog
        isRootNodeSelected={true}
        flowNodeInstanceId="123"
        isVisible={true}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByTestId('data-table-loader')).toBeInTheDocument();
  });

  it('should handle error state', async () => {
    mockQueryAuditLogs().withNetworkError();

    render(
      <OperationsLog
        isRootNodeSelected={true}
        flowNodeInstanceId="123"
        isVisible={true}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        isDismissable: true,
        kind: 'error',
        title: 'Audit logs could not be fetched',
      }),
    );
  });
});
