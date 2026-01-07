/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {BatchOperation} from './index';
import {
  render,
  screen,
  waitForElementToBeRemoved,
  waitFor,
} from 'modules/testing-library';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {Paths} from 'modules/Routes';
import type {BatchOperation as BatchOperationType} from '@camunda/camunda-api-zod-schemas/8.8';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {mockGetBatchOperation} from 'modules/mocks/api/v2/batchOperations/getBatchOperation';
import {notificationsStore} from 'modules/stores/notifications';

const operation: BatchOperationType = {
  batchOperationKey: 'migrate-operation-123',
  batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
  startDate: '2021-02-20T18:31:18.625+0100',
  endDate: '2023-11-22T09:03:29.564+0100',
  state: 'COMPLETED',
  operationsTotalCount: 2,
  operationsCompletedCount: 2,
  operationsFailedCount: 0,
};

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    <MemoryRouter
      initialEntries={[Paths.batchOperation(operation.batchOperationKey)]}
    >
      <Routes>
        <Route path={Paths.batchOperation()} element={children} />
        <Route
          path={Paths.batchOperations()}
          element={<div data-testid="batch-operations" />}
        />
      </Routes>
    </MemoryRouter>
  </QueryClientProvider>
);

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

describe('<BatchOperation />', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetBatchOperation().withSuccess(operation);
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
  });

  it('should render items count summary', async () => {
    render(<BatchOperation />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('text-skeleton'),
    );

    expect(screen.getByText(/2 successful/)).toBeInTheDocument();
  });

  it('should render page title with formatted operation type', async () => {
    render(<BatchOperation />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('text-skeleton'),
    );

    expect(
      screen.getByRole('heading', {name: 'Migrate Process Instance'}),
    ).toBeInTheDocument();
  });

  it('should render operation details tiles', async () => {
    render(<BatchOperation />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('text-skeleton'),
    );

    expect(screen.getByText('State')).toBeInTheDocument();
    expect(screen.getByText('Summary of Items')).toBeInTheDocument();
    expect(screen.getByText('Start time')).toBeInTheDocument();
    expect(screen.getByText('End time')).toBeInTheDocument();
  });

  it('should render batch state indicator with correct state', async () => {
    render(<BatchOperation />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('text-skeleton'),
    );

    expect(screen.getByText('Completed')).toBeInTheDocument();
  });

  it('should render formatted dates for start and end time', async () => {
    render(<BatchOperation />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('text-skeleton'),
    );

    const tiles = screen.getAllByText(/2021|2023/);
    expect(tiles.length).toBeGreaterThan(0);
  });

  it('should show error notification when batch operation fails to load', async () => {
    mockGetBatchOperation().withServerError(500);

    render(<BatchOperation />, {wrapper: Wrapper});

    await waitFor(() => {
      expect(
        screen.getByText('Failed to load batch operation details'),
      ).toBeInTheDocument();
    });
  });

  it('should show forbidden page when user lacks permissions', async () => {
    mockGetBatchOperation().withServerError(403);

    render(<BatchOperation />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('text-skeleton'),
    );

    expect(
      screen.getByText(
        '403 - You do not have permission to view this information',
      ),
    ).toBeInTheDocument();
  });

  it('should redirect to batch operations page and display notification if batch operation is not found (404)', async () => {
    mockGetBatchOperation().withServerError(404);

    render(<BatchOperation />, {wrapper: Wrapper});

    await waitFor(() => {
      expect(screen.getByTestId('batch-operations')).toBeInTheDocument();
    });

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      kind: 'error',
      title: 'batch operation migrate-operation-123 could not be found',
      isDismissable: true,
    });
  });
});
