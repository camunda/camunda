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

const OPERATIONS = {
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
    batchOperationKey: '1d275203-e4e8-4611-87cc-7335a0917a56',
    batchOperationType: 'DELETE_PROCESS_INSTANCE',
    startDate: '2023-12-01T10:15:20.120+0100',
    endDate: '2023-12-01T10:18:45.120+0100',
    operationsFailedCount: 0,
    operationsTotalCount: 3,
    operationsCompletedCount: 3,
    state: 'COMPLETED',
  },
  DELETE_DECISION_INSTANCE: {
    batchOperationKey: '4f6fd527-8c8d-45f0-b5be-7fc05dc8f541',
    batchOperationType: 'DELETE_DECISION_INSTANCE',
    startDate: '2023-12-01T11:05:11.900+0100',
    endDate: '2023-12-01T11:07:31.900+0100',
    operationsFailedCount: 1,
    operationsTotalCount: 5,
    operationsCompletedCount: 4,
    state: 'COMPLETED',
  },
  DELETE_DECISION_DEFINITION: {
    batchOperationKey: 'ab882fe9-1af5-4475-af20-e0672d62a23f',
    batchOperationType: 'DELETE_DECISION_DEFINITION',
    startDate: '2023-12-02T09:22:10.004+0100',
    operationsFailedCount: 0,
    operationsTotalCount: 2,
    operationsCompletedCount: 1,
    state: 'ACTIVE',
  },
  DELETE_PROCESS_DEFINITION: {
    batchOperationKey: '736b55c0-056f-44ee-a0a4-7b9a65da2d78',
    batchOperationType: 'DELETE_PROCESS_DEFINITION',
    startDate: '2023-12-02T13:41:18.601+0100',
    endDate: '2023-12-02T13:43:48.601+0100',
    operationsFailedCount: 0,
    operationsTotalCount: 7,
    operationsCompletedCount: 7,
    state: 'COMPLETED',
  },
  ADD_VARIABLE: {
    batchOperationKey: 'd7cece73-3302-4c00-b990-1bcc59323e7f',
    batchOperationType: 'ADD_VARIABLE',
    startDate: '2023-12-03T08:00:00.000+0100',
    operationsFailedCount: 0,
    operationsTotalCount: 8,
    operationsCompletedCount: 3,
    state: 'ACTIVE',
  },
  UPDATE_VARIABLE: {
    batchOperationKey: 'e3ffca8e-b0f9-4e7b-b6f9-f9744f5a6f2a',
    batchOperationType: 'UPDATE_VARIABLE',
    startDate: '2023-12-03T14:10:45.330+0100',
    endDate: '2023-12-03T14:14:01.330+0100',
    operationsFailedCount: 2,
    operationsTotalCount: 6,
    operationsCompletedCount: 4,
    state: 'COMPLETED',
  },
} as const satisfies Record<BatchOperationType, BatchOperation>;

const mockProps = {
  onInstancesClick: vi.fn(),
};

export {OPERATIONS, mockProps};
