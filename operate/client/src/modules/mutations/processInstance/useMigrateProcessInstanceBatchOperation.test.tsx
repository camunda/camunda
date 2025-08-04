/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {renderHook, waitFor} from 'modules/testing-library';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {useMigrateProcessInstanceBatchOperation} from './useMigrateProcessInstanceBatchOperation';
import {migrateProcessInstanceBatchOperation} from 'modules/api/v2/processInstances/migrateProcessInstanceBatchOperation';
import {queryBatchOperations} from 'modules/api/v2/batchOperations/queryBatchOperations';
import type {
  CreateMigrationBatchOperationRequestBody,
  CreateMigrationBatchOperationResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {act} from 'react';

vi.mock('modules/api/v2/processInstances/migrateProcessInstanceBatchOperation');
vi.mock('modules/api/v2/batchOperations/queryBatchOperations');

const mockMigrateProcessInstanceBatchOperation = vi.mocked(
  migrateProcessInstanceBatchOperation,
);
const mockQueryBatchOperations = vi.mocked(queryBatchOperations);

const createWrapper = (queryClient?: QueryClient) => {
  const client =
    queryClient ||
    new QueryClient({
      defaultOptions: {
        queries: {retry: false},
        mutations: {retry: false},
      },
    });

  const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => {
    return (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
  };

  return {Wrapper, queryClient: client};
};

const mockRequestPayload: CreateMigrationBatchOperationRequestBody = {
  filter: {
    processInstanceKey: {$in: ['123', '456']},
  },
  migrationPlan: {
    targetProcessDefinitionKey: 'target-process-key',
    mappingInstructions: [{sourceElementId: 'task1', targetElementId: 'task2'}],
  },
};

const mockSuccessResponse: CreateMigrationBatchOperationResponseBody = {
  batchOperationKey: 'batch-op-123',
  batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
};

describe('useMigrateProcessInstanceBatchOperation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should create batch operation successfully', async () => {
    const {Wrapper} = createWrapper();

    mockMigrateProcessInstanceBatchOperation.mockResolvedValue(
      mockSuccessResponse,
    );

    mockQueryBatchOperations.mockResolvedValue({
      response: {
        items: [
          {
            batchOperationKey: 'batch-op-123',
            state: 'COMPLETED',
            batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
            startDate: '2023-01-01T00:00:00Z',
            operationsTotalCount: 10,
            operationsFailedCount: 0,
            operationsCompletedCount: 10,
          },
        ],
        page: {totalItems: 1},
      },
      error: null,
    });

    const {result} = renderHook(
      () => useMigrateProcessInstanceBatchOperation(),
      {
        wrapper: Wrapper,
      },
    );

    result.current.mutate(mockRequestPayload);

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(mockMigrateProcessInstanceBatchOperation).toHaveBeenCalledWith(
      mockRequestPayload,
    );
    expect(result.current.data).toEqual(mockSuccessResponse);
  });

  it('should handle batch operation creation failure', async () => {
    const {Wrapper} = createWrapper();

    const error = new Error('Failed to create batch operation');
    mockMigrateProcessInstanceBatchOperation.mockRejectedValue(error);

    const {result} = renderHook(
      () => useMigrateProcessInstanceBatchOperation(),
      {
        wrapper: Wrapper,
      },
    );

    result.current.mutate(mockRequestPayload);

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error).toEqual(error);
  });

  it('should poll batch operation status until completion', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    const {Wrapper} = createWrapper();

    mockMigrateProcessInstanceBatchOperation.mockResolvedValue(
      mockSuccessResponse,
    );

    mockQueryBatchOperations
      .mockResolvedValueOnce({
        response: {
          items: [
            {
              batchOperationKey: 'batch-op-123',
              state: 'ACTIVE',
              batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
              startDate: '2023-01-01T00:00:00Z',
              operationsTotalCount: 10,
              operationsFailedCount: 0,
              operationsCompletedCount: 5,
            },
          ],
          page: {totalItems: 1},
        },
        error: null,
      })
      .mockResolvedValueOnce({
        response: {
          items: [
            {
              batchOperationKey: 'batch-op-123',
              state: 'COMPLETED',
              batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
              startDate: '2023-01-01T00:00:00Z',
              operationsTotalCount: 10,
              operationsFailedCount: 0,
              operationsCompletedCount: 10,
            },
          ],
          page: {totalItems: 1},
        },
        error: null,
      });

    const {result} = renderHook(
      () => useMigrateProcessInstanceBatchOperation(),
      {
        wrapper: Wrapper,
      },
    );

    await act(async () => {
      result.current.mutate(mockRequestPayload);
    });

    act(() => {
      vi.runOnlyPendingTimers();
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(mockQueryBatchOperations).toHaveBeenCalledTimes(2);

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should handle empty batch operations response', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const {Wrapper} = createWrapper();

    mockMigrateProcessInstanceBatchOperation.mockResolvedValue(
      mockSuccessResponse,
    );

    mockQueryBatchOperations
      .mockResolvedValueOnce({
        response: {
          items: [],
          page: {totalItems: 0},
        },
        error: null,
      })
      .mockResolvedValueOnce({
        response: {
          items: [
            {
              batchOperationKey: 'batch-op-123',
              state: 'COMPLETED',
              batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
              startDate: '2023-01-01T00:00:00Z',
              operationsTotalCount: 10,
              operationsFailedCount: 0,
              operationsCompletedCount: 10,
            },
          ],
          page: {totalItems: 1},
        },
        error: null,
      });

    const {result} = renderHook(
      () => useMigrateProcessInstanceBatchOperation(),
      {
        wrapper: Wrapper,
      },
    );

    await act(async () => {
      result.current.mutate(mockRequestPayload);
    });

    act(() => {
      vi.runOnlyPendingTimers();
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(mockQueryBatchOperations).toHaveBeenCalledTimes(2);

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should call onError when mutation fails', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const {Wrapper} = createWrapper();
    const onSuccess = vi.fn();
    const onError = vi.fn();

    const error = new Error('Network error');
    mockMigrateProcessInstanceBatchOperation.mockRejectedValue(error);

    const {result} = renderHook(
      () =>
        useMigrateProcessInstanceBatchOperation({
          onSuccess,
          onError,
        }),
      {wrapper: Wrapper},
    );

    await act(async () => {
      result.current.mutate(mockRequestPayload);
    });

    act(() => {
      vi.runOnlyPendingTimers();
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(onError).toHaveBeenCalledWith(error, mockRequestPayload, undefined);
    expect(onSuccess).not.toHaveBeenCalled();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should handle multiple active operations in batch response', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    const {Wrapper} = createWrapper();

    mockMigrateProcessInstanceBatchOperation.mockResolvedValue(
      mockSuccessResponse,
    );

    mockQueryBatchOperations
      .mockResolvedValueOnce({
        response: {
          items: [
            {
              batchOperationKey: 'batch-op-123',
              state: 'ACTIVE',
              batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
              startDate: '2023-01-01T00:00:00Z',
              operationsTotalCount: 10,
              operationsFailedCount: 0,
              operationsCompletedCount: 5,
            },
            {
              batchOperationKey: 'batch-op-124',
              state: 'ACTIVE',
              batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
              startDate: '2023-01-01T00:00:00Z',
              operationsTotalCount: 5,
              operationsFailedCount: 0,
              operationsCompletedCount: 3,
            },
          ],
          page: {totalItems: 2},
        },
        error: null,
      })
      .mockResolvedValueOnce({
        response: {
          items: [
            {
              batchOperationKey: 'batch-op-123',
              state: 'COMPLETED',
              batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
              startDate: '2023-01-01T00:00:00Z',
              operationsTotalCount: 10,
              operationsFailedCount: 0,
              operationsCompletedCount: 10,
            },
            {
              batchOperationKey: 'batch-op-124',
              state: 'COMPLETED',
              batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
              startDate: '2023-01-01T00:00:00Z',
              operationsTotalCount: 5,
              operationsFailedCount: 0,
              operationsCompletedCount: 5,
            },
          ],
          page: {totalItems: 2},
        },
        error: null,
      });

    const {result} = renderHook(
      () => useMigrateProcessInstanceBatchOperation(),
      {
        wrapper: Wrapper,
      },
    );

    await act(async () => {
      result.current.mutate(mockRequestPayload);
    });

    act(() => {
      vi.runOnlyPendingTimers();
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(mockQueryBatchOperations).toHaveBeenCalledTimes(2);

    vi.clearAllTimers();
    vi.useRealTimers();
  });
});
