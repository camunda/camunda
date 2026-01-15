/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClientProvider} from '@tanstack/react-query';
import {
  render,
  screen,
  waitForElementToBeRemoved,
  waitFor,
} from 'modules/testing-library';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {MemoryRouter} from 'react-router-dom';
import {mockQueryBatchOperations} from 'modules/mocks/api/v2/batchOperations/queryBatchOperations';
import {BatchOperations} from './';
import type {BatchOperation} from '@camunda/camunda-api-zod-schemas/8.8';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <QueryClientProvider client={getMockQueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
};

const operation: BatchOperation = {
  batchOperationKey: 'migrate-operation-123',
  batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
  startDate: '2021-02-20T18:31:18.625+0100',
  endDate: '2023-11-22T09:03:29.564+0100',
  state: 'COMPLETED',
  operationsTotalCount: 2,
  operationsCompletedCount: 2,
  operationsFailedCount: 0,
  actorId: 'demo',
};

const createMockOperations = (count: number): BatchOperation[] => {
  return Array.from({length: count}, (_, i) => ({
    ...operation,
    batchOperationKey: `operation-${i + 1}`,
  }));
};

describe('<BatchOperations />', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockQueryBatchOperations().withSuccess({
      items: [operation],
      page: {totalItems: 1},
    });
  });

  it('should render skeleton state when loading', async () => {
    render(<BatchOperations />, {wrapper: Wrapper});

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.queryByTestId('data-table-skeleton')).not.toBeInTheDocument();
  });

  it('should render empty state when no operations exist', async () => {
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
    render(<BatchOperations />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByText('No batch operations found')).toBeInTheDocument();
    expect(
      screen.getByText('Try adjusting your filters or check back later.'),
    ).toBeInTheDocument();
  });

  it('should render table rows with formatted data', async () => {
    render(<BatchOperations />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByTestId('cell-operationType').textContent).toBe(
      'Migrate Process Instance',
    );
    expect(screen.getByTestId('cell-actor').textContent).toBe(
      operation.actorId,
    );
  });

  it('should hide pagination when no operations exist', async () => {
    mockQueryBatchOperations().withSuccess({
      items: [],
      page: {totalItems: 0},
    });

    render(<BatchOperations />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );
    expect(screen.queryByText('Items per page:')).not.toBeInTheDocument();
  });

  it('should show pagination when operations exist', async () => {
    const mockOperations = createMockOperations(20);
    mockQueryBatchOperations().withSuccess({
      items: mockOperations,
      page: {totalItems: 50},
    });

    render(<BatchOperations />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByText('Items per page:')).toBeInTheDocument();
    expect(screen.getByText('1–20 of 50 items')).toBeInTheDocument();
  });

  it('should navigate to next page when next button is clicked', async () => {
    const firstPageOperations = createMockOperations(20);
    const secondPageOperations = createMockOperations(20).map((op, i) => ({
      ...op,
      batchOperationKey: `operation-page-2-${i + 1}`,
    }));

    mockQueryBatchOperations().withSuccess({
      items: firstPageOperations,
      page: {totalItems: 50},
    });

    const {user} = render(<BatchOperations />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByText('1–20 of 50 items')).toBeInTheDocument();

    mockQueryBatchOperations().withSuccess({
      items: secondPageOperations,
      page: {totalItems: 50},
    });

    const nextButton = screen.getByRole('button', {name: /next page/i});
    await user.click(nextButton);

    await waitFor(() => {
      expect(screen.getByText('21–40 of 50 items')).toBeInTheDocument();
    });
  });

  it('should change page size when selecting different items per page', async () => {
    mockQueryBatchOperations().withSuccess({
      items: createMockOperations(20),
      page: {totalItems: 100},
    });

    const {user} = render(<BatchOperations />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(screen.getByText('1–20 of 100 items')).toBeInTheDocument();

    mockQueryBatchOperations().withSuccess({
      items: createMockOperations(50),
      page: {totalItems: 100},
    });

    const pageSizeSelect = screen.getByRole('combobox', {
      name: /items per page/i,
    });
    await user.click(pageSizeSelect);
    await user.selectOptions(pageSizeSelect, '50');

    await waitFor(() => {
      expect(screen.getByText('1–50 of 100 items')).toBeInTheDocument();
    });
  });

  it('should hide pagination when there is only one page', async () => {
    mockQueryBatchOperations().withSuccess({
      items: createMockOperations(5),
      page: {totalItems: 5},
    });

    render(<BatchOperations />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(
      screen.queryByTestId('data-table-skeleton'),
    );

    expect(
      screen.queryByRole('combobox', {
        name: /items per page/i,
      }),
    ).not.toBeInTheDocument();
  });
});
