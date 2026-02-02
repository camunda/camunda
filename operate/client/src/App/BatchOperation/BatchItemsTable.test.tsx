/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BatchOperationItem} from '@camunda/camunda-api-zod-schemas/8.8';
import {QueryClientProvider} from '@tanstack/react-query';
import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {mockQueryBatchOperationItems} from 'modules/mocks/api/v2/batchOperations/queryBatchOperationItems';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {BatchItemsTable} from './BatchItemsTable';
import {MemoryRouter} from 'react-router-dom';

const BATCH_OPERATION_KEY = 'migrate-operation-123';

const createMockBatchOperationItems = (
  count: number,
  options?: {
    withErrors?: boolean;
    state?: BatchOperationItem['state'];
    operationType?: BatchOperationItem['operationType'];
  },
): BatchOperationItem[] => {
  return Array.from({length: count}, (_, i) => ({
    batchOperationKey: BATCH_OPERATION_KEY,
    itemKey: `item-${i + 1}`,
    processInstanceKey: `2251799813685${250 + i}`,
    state: options?.state ?? 'COMPLETED',
    operationType: options?.operationType ?? 'CANCEL_PROCESS_INSTANCE',
    processedDate: '2023-11-22T09:03:29.564+0100',
    errorMessage: options?.withErrors
      ? `Error processing item ${i + 1}`
      : undefined,
  }));
};

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    <MemoryRouter>{children}</MemoryRouter>
  </QueryClientProvider>
);

describe('<BatchItemsTable />', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockQueryBatchOperationItems().withSuccess({
      items: [],
      page: {totalItems: 0},
    });
  });

  it('should render empty state when no items exist', async () => {
    render(
      <BatchItemsTable
        batchOperationKey={BATCH_OPERATION_KEY}
        isLoading={false}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('data-table-skeleton'),
    );

    expect(screen.queryByText('0 Items')).not.toBeInTheDocument();
    expect(screen.getByText('No items found')).toBeInTheDocument();
  });

  it('should render skeleton state when loading', async () => {
    render(
      <BatchItemsTable
        batchOperationKey={BATCH_OPERATION_KEY}
        isLoading={false}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByTestId('data-table-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('data-table-skeleton'),
    );

    expect(screen.queryByTestId('data-table-skeleton')).not.toBeInTheDocument();
  });

  it('should render table with batch operation items', async () => {
    const items = createMockBatchOperationItems(3);
    mockQueryBatchOperationItems().withSuccess({
      items,
      page: {totalItems: 3},
    });

    render(
      <BatchItemsTable
        batchOperationKey={BATCH_OPERATION_KEY}
        isLoading={false}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('data-table-skeleton'),
    );

    expect(screen.getByText('3 Items')).toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        name: /view process instance 2251799813685250/i,
      }),
    ).toHaveAttribute('href', '/processes/2251799813685250');
    expect(screen.getByText('2251799813685251')).toBeInTheDocument();
    expect(screen.getByText('2251799813685252')).toBeInTheDocument();
  });

  it('should render items with failed state', async () => {
    const items = createMockBatchOperationItems(2, {state: 'FAILED'});
    mockQueryBatchOperationItems().withSuccess({
      items,
      page: {totalItems: 2},
    });

    render(
      <BatchItemsTable
        batchOperationKey={BATCH_OPERATION_KEY}
        isLoading={false}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('data-table-skeleton'),
    );

    const failedStates = screen.getAllByText('Failed');
    expect(failedStates.length).toBeGreaterThan(0);
  });

  it('should render error messages for failed items', async () => {
    const items = createMockBatchOperationItems(1, {
      withErrors: true,
      state: 'FAILED',
    });
    mockQueryBatchOperationItems().withSuccess({
      items,
      page: {totalItems: 1},
    });

    render(
      <BatchItemsTable
        batchOperationKey={BATCH_OPERATION_KEY}
        isLoading={false}
      />,
      {
        wrapper: Wrapper,
      },
    );

    await waitForElementToBeRemoved(() =>
      screen.queryAllByTestId('data-table-skeleton'),
    );
    expect(screen.getByText('Failure reason:')).toBeInTheDocument();
    expect(screen.getByText('Error processing item 1')).toBeInTheDocument();
  });
});
