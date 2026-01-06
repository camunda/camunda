/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  BatchOperation,
  BatchOperationType,
} from '@camunda/camunda-api-zod-schemas/8.8';

const OPERATIONS: Record<
  BatchOperationType,
  Omit<BatchOperation, 'batchOperationType'> & {
    batchOperationType: BatchOperationType;
  }
> = {
  RESOLVE_INCIDENT: {
    batchOperationKey: 'b42fd629-73b1-4709-befb-7ccd900fb18d',
    batchOperationType: 'RESOLVE_INCIDENT',
    operationsTotalCount: 2,
    operationsCompletedCount: 1,
    operationsFailedCount: 0,
    startDate: '2021-02-20T18:31:18.625+0100',
    state: 'ACTIVE',
  },
  CANCEL_PROCESS_INSTANCE: {
    batchOperationKey: '393ad666-d7f0-45c9-a679-ffa0ef82f88a',
    batchOperationType: 'CANCEL_PROCESS_INSTANCE',
    endDate: '2023-11-22T09:03:29.564+0100',
    operationsTotalCount: 2,
    operationsCompletedCount: 2,
    operationsFailedCount: 0,
    startDate: '2021-02-20T18:31:18.625+0100',
    state: 'COMPLETED',
  },

  MODIFY_PROCESS_INSTANCE: {
    batchOperationKey: 'df325d44-6a4c-4428-b017-24f923f1d052',
    batchOperationType: 'MODIFY_PROCESS_INSTANCE',
    endDate: '2023-11-22T09:03:29.564+0100',
    operationsTotalCount: 4,
    operationsCompletedCount: 4,
    operationsFailedCount: 0,
    startDate: '2021-02-20T18:31:18.625+0100',
    state: 'COMPLETED',
  },

  MIGRATE_PROCESS_INSTANCE: {
    batchOperationKey: '8ba1a9a7-8537-4af3-97dc-f7249743b20b',
    batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
    startDate: '2023-10-22T09:02:30.178+0100',
    endDate: '2023-11-22T09:03:29.564+0100',
    operationsFailedCount: 0,
    operationsTotalCount: 1,
    operationsCompletedCount: 1,
    state: 'COMPLETED',
  },
  DELETE_PROCESS_INSTANCE: {
    batchOperationKey: 'delete-pi-12345-67890-abcdef',
    batchOperationType: 'DELETE_PROCESS_INSTANCE',
    startDate: '2023-11-22T09:02:30.178+0100',
    endDate: '2023-11-22T09:03:29.564+0100',
    operationsFailedCount: 0,
    operationsTotalCount: 3,
    operationsCompletedCount: 3,
    state: 'COMPLETED',
  },
  DELETE_PROCESS_DEFINITION: {
    batchOperationKey: 'delete-pd-98765-43210-fedcba',
    batchOperationType: 'DELETE_PROCESS_DEFINITION',
    startDate: '2023-11-22T09:02:30.178+0100',
    endDate: '2023-11-22T09:03:29.564+0100',
    operationsFailedCount: 1,
    operationsTotalCount: 2,
    operationsCompletedCount: 1,
    state: 'COMPLETED',
  },
  DELETE_DECISION_DEFINITION: {
    batchOperationKey: 'delete-dd-11111-22222-33333',
    batchOperationType: 'DELETE_DECISION_DEFINITION',
    startDate: '2023-11-22T09:02:30.178+0100',
    endDate: '2023-11-22T09:03:29.564+0100',
    operationsFailedCount: 0,
    operationsTotalCount: 1,
    operationsCompletedCount: 1,
    state: 'COMPLETED',
  },
  ADD_VARIABLE: {
    batchOperationKey: 'add-var-44444-55555-66666',
    batchOperationType: 'ADD_VARIABLE',
    startDate: '2023-11-22T10:00:00.000+0100',
    endDate: '2023-11-22T10:05:00.000+0100',
    operationsFailedCount: 0,
    operationsTotalCount: 2,
    operationsCompletedCount: 2,
    state: 'COMPLETED',
  },
  UPDATE_VARIABLE: {
    batchOperationKey: 'update-var-77777-88888-99999',
    batchOperationType: 'UPDATE_VARIABLE',
    startDate: '2023-11-22T11:00:00.000+0100',
    endDate: '2023-11-22T11:05:00.000+0100',
    operationsFailedCount: 0,
    operationsTotalCount: 1,
    operationsCompletedCount: 1,
    state: 'COMPLETED',
  },
} as const;

const mockProps = {
  onInstancesClick: vi.fn(),
};

export {OPERATIONS, mockProps};
