/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect, beforeEach, vi, type Mock} from 'vitest';
import {OperationsLog} from './index';
import {render, screen} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import * as auditLogsModule from 'modules/queries/auditLog/useAuditLogs.ts';
import {MemoryRouter} from 'react-router-dom';
import React from 'react';
import {notificationsStore} from '../../../../../modules/stores/notifications.tsx';

vi.mock('modules/queries/auditLog/useAuditLogs.ts');
vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const useAuditLogsSpy = auditLogsModule.useAuditLogs as unknown as Mock;

const Wrapper = ({children}: {children?: React.ReactNode}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
};

describe('OperationsLog', () => {
  beforeEach(() => {
    useAuditLogsSpy.mockReset();
  });

  it('should show skeleton state when data undefined', () => {
    useAuditLogsSpy.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: null,
    });

    render(<OperationsLog flowNodeInstanceId="123" isVisible={false} />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();
  });

  it('should render empty state when no items are present', () => {
    useAuditLogsSpy.mockReturnValue({
      data: {items: []},
      isLoading: false,
      error: null,
    });

    render(<OperationsLog flowNodeInstanceId="123" isVisible />, {
      wrapper: Wrapper,
    });

    expect(
      screen.getByText('No operations found for this instance'),
    ).toBeInTheDocument();
  });

  it('should render rows when data is present', () => {
    const item = {
      auditLogKey: '1',
      entityKey: '1',
      operationType: 'UPDATE',
      entityType: 'VARIABLE',
      result: 'SUCCESS',
      actorId: 'user1',
      timestamp: '2024-01-01T00:00:00.000Z',
      description: 'Updated variable',
      details: {},
    } as const;

    useAuditLogsSpy.mockReturnValue({
      data: {
        items: [item],
      },
      isLoading: false,
      error: null,
    });

    render(<OperationsLog flowNodeInstanceId="123" isVisible />, {
      wrapper: Wrapper,
    });

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

    expect(screen.getByText(/update variable/i)).toBeInTheDocument();
    expect(screen.getByText(/success/i)).toBeInTheDocument();
    expect(screen.getByText('user1')).toBeInTheDocument();
    expect(screen.getByText('2024-01-01 00:00:00')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /open details/i}),
    ).toBeInTheDocument();
  });

  it('should handle loading state', () => {
    useAuditLogsSpy.mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    });

    render(<OperationsLog flowNodeInstanceId="123" isVisible />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTestId('data-table-loader')).toBeInTheDocument();
  });

  it('should handle error state', () => {
    useAuditLogsSpy.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Failed to fetch'),
    });

    render(<OperationsLog flowNodeInstanceId="123" isVisible />, {
      wrapper: Wrapper,
    });

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'error',
      title: 'Audit logs could not be fetched',
    });
  });
});
