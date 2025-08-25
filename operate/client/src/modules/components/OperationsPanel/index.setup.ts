/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BatchOperation} from '@vzeta/camunda-api-zod-schemas/8.8';

const mockOperationRunning: BatchOperation = {
  batchOperationKey: '1234',
  batchOperationType: 'RESOLVE_INCIDENT',
  startDate: '2020-02-06T14:37:29.699+0100',
  endDate: undefined,
  operationsFailedCount: 1,
  operationsTotalCount: 1,
  operationsCompletedCount: 0,
  state: 'ACTIVE',
};

const mockOperationFinished: BatchOperation = {
  batchOperationKey: '5678',
  batchOperationType: 'CANCEL_PROCESS_INSTANCE',
  startDate: '2020-02-06T14:37:29.699+0100',
  endDate: '2020-02-06T15:37:29.699+0100',
  operationsFailedCount: 4,
  operationsTotalCount: 2,
  operationsCompletedCount: 2,
  state: 'COMPLETED',
};

export {mockOperationRunning, mockOperationFinished};
