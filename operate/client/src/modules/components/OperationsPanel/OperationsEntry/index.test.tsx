/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState, useLayoutEffect} from 'react';
import {MemoryRouter} from 'react-router-dom';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {OPERATIONS, mockProps} from './index.setup';
import {OperationsEntry} from './index';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';
import {Filters} from 'App/Processes/ListView/Filters';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';

function createWrapper() {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <MemoryRouter>
        {children}
        <LocationLog />
      </MemoryRouter>
    );
  };

  return Wrapper;
}

const FinishingOperationsEntry: React.FC = () => {
  const [completedCount, setCompletedCount] = useState(0);

  useLayoutEffect(() => {
    setCompletedCount(5);
  }, []);

  return (
    <OperationsEntry
      operation={{
        ...OPERATIONS.CANCEL_PROCESS_INSTANCE,
        operationsTotalCount: 5,
        operationsCompletedCount: completedCount,
      }}
    />
  );
};

const OPERATIONS_TIMESTAMP = '2023-11-22 08:03:29';

describe('OperationsEntry', () => {
  it('should render retry operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={OPERATIONS.RESOLVE_INCIDENT}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
    expect(
      screen.getByText('b42fd629-73b1-4709-befb-7ccd900fb18d'),
    ).toBeInTheDocument();
    expect(screen.getByText('Retry')).toBeInTheDocument();
    expect(screen.getByTestId('operation-retry-icon')).toBeInTheDocument();
  });

  it('should render cancel operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={OPERATIONS.CANCEL_PROCESS_INSTANCE}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(OPERATIONS_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('393ad666-d7f0-45c9-a679-ffa0ef82f88a'),
    ).toBeInTheDocument();
    expect(screen.getByText('Cancel')).toBeInTheDocument();
    expect(screen.getByTestId('operation-cancel-icon')).toBeInTheDocument();
  });

  it('should render modify operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={OPERATIONS.MODIFY_PROCESS_INSTANCE}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(OPERATIONS_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('df325d44-6a4c-4428-b017-24f923f1d052'),
    ).toBeInTheDocument();
    expect(screen.getByText('Modify')).toBeInTheDocument();
    expect(screen.getByTestId('operation-modify-icon')).toBeInTheDocument();
  });

  it('should render migrate operation', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={OPERATIONS.MIGRATE_PROCESS_INSTANCE}
      />,
      {
        wrapper: createWrapper(),
      },
    );

    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByText(OPERATIONS_TIMESTAMP)).toBeInTheDocument();
    expect(
      screen.getByText('8ba1a9a7-8537-4af3-97dc-f7249743b20b'),
    ).toBeInTheDocument();
    expect(screen.getByText('Migrate')).toBeInTheDocument();
    expect(screen.getByTestId('operation-migrate-icon')).toBeInTheDocument();
  });

  it('should render id link for non-delete instance operations', () => {
    render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.CANCEL_PROCESS_INSTANCE,
          operationsTotalCount: 6,
          operationsFailedCount: 3,
          operationsCompletedCount: 3,
        }}
      />,
      {wrapper: createWrapper()},
    );

    expect(
      screen.getByRole('link', {
        name: OPERATIONS.CANCEL_PROCESS_INSTANCE.batchOperationKey,
      }),
    ).toBeInTheDocument();
  });

  it('should filter by Operation and expand Filters Panel', async () => {
    panelStatesStore.toggleFiltersPanel();

    const {user} = render(
      <OperationsEntry
        {...mockProps}
        operation={{
          ...OPERATIONS.CANCEL_PROCESS_INSTANCE,
          operationsTotalCount: 1,
        }}
      />,
      {wrapper: createWrapper()},
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    await user.click(
      screen.getByText(OPERATIONS.CANCEL_PROCESS_INSTANCE.batchOperationKey),
    );
    expect(screen.getByTestId('search')).toHaveTextContent(
      /^\?active=true&incidents=true&completed=true&canceled=true&operationId=393ad666-d7f0-45c9-a679-ffa0ef82f88a$/,
    );

    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);
  });

  it('should not remove optional operation id filter when operation filter is applied twice', async () => {
    mockMe().withSuccess(createUser());
    const {user} = render(
      <QueryClientProvider client={getMockQueryClient()}>
        <OperationsEntry
          {...mockProps}
          operation={{
            ...OPERATIONS.CANCEL_PROCESS_INSTANCE,
            operationsTotalCount: 1,
          }}
        />
        <Filters />
      </QueryClientProvider>,
      {wrapper: createWrapper()},
    );

    expect(screen.queryByLabelText(/^operation id$/i)).not.toBeInTheDocument();

    await user.click(
      screen.getByText(OPERATIONS.CANCEL_PROCESS_INSTANCE.batchOperationKey),
    );

    expect(await screen.findByLabelText(/^operation id$/i)).toBeInTheDocument();

    await user.click(
      screen.getByText(OPERATIONS.CANCEL_PROCESS_INSTANCE.batchOperationKey),
    );
    expect(screen.getByLabelText(/^operation id$/i)).toBeInTheDocument();
  });

  it('should fake the first 10% progress', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    render(
      <OperationsEntry
        operation={{
          ...OPERATIONS.CANCEL_PROCESS_INSTANCE,
          endDate: undefined,
          operationsTotalCount: 10,
          operationsCompletedCount: 0,
          operationsFailedCount: 0,
        }}
      />,
      {wrapper: createWrapper()},
    );

    expect(screen.getByRole('progressbar')).toHaveAttribute(
      'aria-valuenow',
      '0',
    );

    vi.runOnlyPendingTimersAsync();
    await waitFor(() =>
      expect(screen.getByRole('progressbar')).toHaveAttribute(
        'aria-valuenow',
        '10',
      ),
    );
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should render 50% progress and fake progress', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    render(
      <OperationsEntry
        operation={{
          ...OPERATIONS.CANCEL_PROCESS_INSTANCE,
          endDate: undefined,
          operationsTotalCount: 10,
          operationsCompletedCount: 5,
          operationsFailedCount: 0,
        }}
      />,
      {wrapper: createWrapper()},
    );

    vi.runOnlyPendingTimersAsync();
    await waitFor(() =>
      expect(screen.getByRole('progressbar')).toHaveAttribute(
        'aria-valuenow',
        '50',
      ),
    );

    vi.runOnlyPendingTimersAsync();
    await waitFor(() =>
      expect(screen.getByRole('progressbar')).toHaveAttribute(
        'aria-valuenow',
        '55',
      ),
    );
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should render 100% progress and hide progress bar', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    render(<FinishingOperationsEntry />, {wrapper: createWrapper()});

    await waitFor(() =>
      expect(screen.getByRole('progressbar')).toHaveAttribute(
        'aria-valuenow',
        '100',
      ),
    );

    expect(screen.queryByText(OPERATIONS_TIMESTAMP)).not.toBeInTheDocument();

    vi.runOnlyPendingTimersAsync();
    await waitForElementToBeRemoved(screen.queryByRole('progressbar'));
    expect(screen.getByText(OPERATIONS_TIMESTAMP)).toBeInTheDocument();
    vi.clearAllTimers();
    vi.useRealTimers();
  });
});
