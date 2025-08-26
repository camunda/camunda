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
} from '@vzeta/camunda-api-zod-schemas/8.8';

const OPERATIONS: Record<BatchOperationType, BatchOperation> = {
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
} as const;

const mockProps = {
  onInstancesClick: vi.fn(),
};

export {OPERATIONS, mockProps};
