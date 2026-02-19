/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {OperationsLog} from './index';
import {render, screen, waitFor} from 'modules/testing-library';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {mockQueryAuditLogs} from 'modules/mocks/api/v2/auditLogs/queryAuditLogs';
import {notificationsStore} from 'modules/stores/notifications';
import {Paths} from 'modules/Routes';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchElementInstances} from 'modules/mocks/api/v2/elementInstances/searchElementInstances';
import {mockProcessInstance} from 'modules/mocks/api/v2/mocks/processInstance';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const getWrapper = (searchParams?: Record<string, string>) => {
  const params = new URLSearchParams(searchParams);
  const Wrapper = ({children}: {children?: React.ReactNode}) => (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter
        initialEntries={[
          `${Paths.processInstance(mockProcessInstance.processInstanceKey)}?${params.toString()}`,
        ]}
      >
        <Routes>
          <Route path={Paths.processInstance()} element={children} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
  return Wrapper;
};

describe('OperationsLog', () => {
  it('should show skeleton state when data undefined', () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockQueryAuditLogs().withServerError();

    render(<OperationsLog isVisible={false} />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();
  });

  it('should render empty state when no items are present', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockQueryAuditLogs().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    render(<OperationsLog isVisible />, {
      wrapper: getWrapper(),
    });

    expect(
      await screen.findByText('No operations found for this instance'),
    ).toBeInTheDocument();
  });

  it('should render rows when data is present', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
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
          entityDescription: 'variableValue',
        },
      ],
      page: {totalItems: 1},
    });

    render(<OperationsLog isVisible />, {
      wrapper: getWrapper(),
    });

    expect(
      screen.getByRole('columnheader', {name: /operation type/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /entity type/i}),
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

    expect(await screen.findByTestId('SUCCESS-icon')).toBeInTheDocument();
    expect(await screen.findByText('Update')).toBeInTheDocument();
    expect(await screen.findByText('Variable')).toBeInTheDocument();
    expect(screen.getByText(/variable name/i)).toBeInTheDocument();
    expect(screen.getByText('variableValue')).toBeInTheDocument();
    expect(screen.getAllByText('user1').at(0)).toBeInTheDocument();
    expect(screen.getByText('2024-01-01 00:00:00')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /open details/i}),
    ).toBeInTheDocument();
  });

  it('should handle loading state', () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockQueryAuditLogs().withDelay({
      items: [],
      page: {totalItems: 0},
    });

    render(<OperationsLog isVisible />, {
      wrapper: getWrapper(),
    });

    expect(screen.getByTestId('data-table-loader')).toBeInTheDocument();
  });

  it('should handle error state', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockQueryAuditLogs().withNetworkError();

    render(<OperationsLog isVisible />, {
      wrapper: getWrapper(),
    });

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        isDismissable: true,
        kind: 'error',
        title: 'Audit logs could not be fetched',
      }),
    );
  });

  it('should show a warning if multiple element instances are selected', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockSearchElementInstances().withSuccess({
      items: [],
      page: {totalItems: 2},
    });

    render(<OperationsLog isVisible />, {
      wrapper: getWrapper({elementId: 'Activity_1'}),
    });

    expect(
      await screen.findByText(
        'To view the Operations Log, select a single Element Instance in the Instance History.',
      ),
    ).toBeInTheDocument();
  });
});
