/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Route} from '@playwright/test';
import {
  type QueryBatchOperationsResponseBody,
  type QueryBatchOperationItemsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';

function mockResponses({
  batchOperations,
  batchOperationItems,
}: {
  batchOperations?: QueryBatchOperationsResponseBody;
  batchOperationItems?: QueryBatchOperationItemsResponseBody;
}) {
  return (route: Route) => {
    if (route.request().url().includes('/v2/authentication/me')) {
      return route.fulfill({
        status: 200,
        body: JSON.stringify({
          displayName: 'demo',
          canLogout: true,
          roles: null,
          salesPlanType: null,
          c8Links: {},
          username: 'demo',
        }),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/v2/batch-operations/search')) {
      return route.fulfill({
        status: batchOperations === undefined ? 400 : 200,
        body: JSON.stringify(batchOperations),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    const batchOperationMatch = route
      .request()
      .url()
      .match(/\/v2\/batch-operations\/([^/]+)$/);
    if (batchOperationMatch && route.request().method() === 'GET') {
      const batchOperationKey = batchOperationMatch[1];
      const operation = batchOperations?.items.find(
        (op) => op.batchOperationKey === batchOperationKey,
      );

      return route.fulfill({
        status: operation ? 200 : 404,
        body: operation
          ? JSON.stringify(operation)
          : JSON.stringify({error: 'Not found'}),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    if (route.request().url().includes('/v2/batch-operation-items/search')) {
      return route.fulfill({
        status: batchOperationItems === undefined ? 400 : 200,
        body: JSON.stringify(batchOperationItems),
        headers: {
          'content-type': 'application/json',
        },
      });
    }

    return route.continue();
  };
}

const mockBatchOperations: QueryBatchOperationsResponseBody = {
  items: [
    {
      batchOperationKey: '653ed5e6-49ed-4675-85bf-2c54a94d8180',
      batchOperationType: 'RESOLVE_INCIDENT',
      startDate: '2023-08-25T15:41:45.322+0300',
      endDate: '2023-08-25T15:41:49.754+0300',
      operationsTotalCount: 3,
      operationsFailedCount: 0,
      operationsCompletedCount: 3,
      state: 'COMPLETED',
      actorId: 'demo',
      actorType: null,
      errors: [],
    },
    {
      batchOperationKey: '35ccdcfc-aeac-4ec8-ac6c-db67e581b22e',
      batchOperationType: 'MODIFY_PROCESS_INSTANCE',
      startDate: '2023-08-15T10:42:17.548+0300',
      endDate: '2023-08-15T10:42:18.818+0300',
      operationsTotalCount: 5,
      operationsFailedCount: 2,
      operationsCompletedCount: 3,
      state: 'COMPLETED',
      actorId: 'demo',
      actorType: null,
      errors: [],
    },
    {
      batchOperationKey: 'fb7cfeb0-abaa-4323-8910-9d44fe031c08',
      batchOperationType: 'CANCEL_PROCESS_INSTANCE',
      startDate: '2023-08-14T08:46:05.677+0300',
      endDate: '2023-08-14T08:46:25.020+0300',
      operationsTotalCount: 3,
      operationsFailedCount: 3,
      operationsCompletedCount: 0,
      state: 'COMPLETED',
      actorId: 'admin',
      actorType: null,
      errors: [],
    },
    {
      batchOperationKey: 'c1331a55-3f6f-4884-837f-dfa268f7ef0c',
      batchOperationType: 'CANCEL_PROCESS_INSTANCE',
      startDate: '2023-08-14T08:46:05.459+0300',
      endDate: '2023-08-14T08:46:25.010+0300',
      operationsTotalCount: 1,
      operationsFailedCount: 1,
      operationsCompletedCount: 0,
      state: 'COMPLETED',
      actorId: 'admin',
      actorType: null,
      errors: [],
    },
    {
      batchOperationKey: 'a74db3d1-4588-41a5-9e10-42cea80213a6',
      batchOperationType: 'CANCEL_PROCESS_INSTANCE',
      startDate: '2023-08-14T08:46:06.164+0300',
      endDate: '2023-08-14T08:46:14.965+0300',
      operationsTotalCount: 1,
      operationsFailedCount: 0,
      operationsCompletedCount: 1,
      state: 'COMPLETED',
      actorId: 'demo',
      actorType: null,
      errors: [],
    },
    {
      batchOperationKey: '9961d35a-261f-4b29-b506-8b14cc6e7992',
      batchOperationType: 'CANCEL_PROCESS_INSTANCE',
      startDate: '2023-08-14T08:46:05.569+0300',
      endDate: '2023-08-14T08:46:14.942+0300',
      operationsTotalCount: 1,
      operationsFailedCount: 0,
      operationsCompletedCount: 1,
      state: 'COMPLETED',
      actorId: 'demo',
      actorType: null,
      errors: [],
    },
    {
      batchOperationKey: 'b1454600-5f13-4365-bb45-960e8372136b',
      batchOperationType: 'RESOLVE_INCIDENT',
      startDate: '2023-08-18T13:14:32.297+0300',
      endDate: '2023-08-18T13:14:37.023+0300',
      operationsTotalCount: 1,
      operationsFailedCount: 1,
      operationsCompletedCount: 0,
      state: 'PARTIALLY_COMPLETED',
      actorId: 'demo',
      actorType: null,
      errors: [],
    },
    {
      batchOperationKey: 'f9ddd801-ff34-44da-8d7c-366036b6d8d8',
      batchOperationType: 'CANCEL_PROCESS_INSTANCE',
      startDate: '2023-08-14T08:46:06.344+0300',
      endDate: '2023-08-14T08:46:14.987+0300',
      operationsTotalCount: 1,
      operationsFailedCount: 0,
      operationsCompletedCount: 1,
      state: 'COMPLETED',
      actorId: 'admin',
      actorType: null,
      errors: [],
    },
    {
      batchOperationKey: 'c5e97ca8-bdf9-434f-934f-506a6960d1e3',
      batchOperationType: 'RESOLVE_INCIDENT',
      startDate: '2023-08-15T13:17:32.235+0300',
      endDate: '2023-08-15T13:17:36.637+0300',
      operationsTotalCount: 1,
      operationsFailedCount: 0,
      operationsCompletedCount: 1,
      state: 'COMPLETED',
      actorId: 'demo',
      actorType: null,
      errors: [],
    },
    {
      batchOperationKey: '653ed5e6-49ed-4675-85bf-2c54a94d8199',
      batchOperationType: 'MIGRATE_PROCESS_INSTANCE',
      startDate: '2023-10-01T10:00:00.000+0000',
      endDate: null,
      operationsTotalCount: 3,
      operationsFailedCount: 0,
      operationsCompletedCount: 0,
      state: 'ACTIVE',
      actorId: 'demo',
      actorType: null,
      errors: [],
    },
  ],
  page: {
    totalItems: 10,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
};

const mockBatchOperationItems: QueryBatchOperationItemsResponseBody = {
  items: [
    {
      batchOperationKey: '653ed5e6-49ed-4675-85bf-2c54a94d8180',
      itemKey: 'item-1',
      processInstanceKey: '6755399441062827',
      rootProcessInstanceKey: null,
      state: 'COMPLETED',
      processedDate: '2023-08-25T15:41:49.754+0300',
      errorMessage: null,
      operationType: 'RESOLVE_INCIDENT',
    },
    {
      batchOperationKey: '653ed5e6-49ed-4675-85bf-2c54a94d8180',
      itemKey: 'item-2',
      processInstanceKey: '6755399441062826',
      rootProcessInstanceKey: null,
      state: 'COMPLETED',
      processedDate: '2023-08-25T15:41:48.500+0300',
      errorMessage: null,
      operationType: 'RESOLVE_INCIDENT',
    },
    {
      batchOperationKey: '653ed5e6-49ed-4675-85bf-2c54a94d8180',
      itemKey: 'item-3',
      processInstanceKey: '6755399441062825',
      rootProcessInstanceKey: null,
      state: 'COMPLETED',
      processedDate: '2023-08-25T15:41:47.200+0300',
      errorMessage: null,
      operationType: 'RESOLVE_INCIDENT',
    },
  ],
  page: {
    totalItems: 3,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
};

const mockBatchOperationItemsWithError: QueryBatchOperationItemsResponseBody = {
  items: [
    {
      batchOperationKey: '35ccdcfc-aeac-4ec8-ac6c-db67e581b22e',
      itemKey: 'error-item-1',
      processInstanceKey: '6755399441062834',
      rootProcessInstanceKey: null,
      state: 'FAILED',
      processedDate: '2023-08-15T10:42:18.818+0300',
      errorMessage: 'Failed to modify process instance: Invalid element',
      operationType: 'MODIFY_PROCESS_INSTANCE',
    },
    {
      batchOperationKey: '35ccdcfc-aeac-4ec8-ac6c-db67e581b22e',
      itemKey: 'error-item-2',
      processInstanceKey: '6755399441062833',
      rootProcessInstanceKey: null,
      state: 'FAILED',
      processedDate: '2023-08-15T10:42:18.500+0300',
      errorMessage: 'Failed to modify process instance: Element not found',
      operationType: 'MODIFY_PROCESS_INSTANCE',
    },
    {
      batchOperationKey: '35ccdcfc-aeac-4ec8-ac6c-db67e581b22e',
      itemKey: 'item-3',
      processInstanceKey: '6755399441062832',
      rootProcessInstanceKey: null,
      state: 'COMPLETED',
      processedDate: '2023-08-15T10:42:18.200+0300',
      errorMessage: null,
      operationType: 'MODIFY_PROCESS_INSTANCE',
    },
    {
      batchOperationKey: '35ccdcfc-aeac-4ec8-ac6c-db67e581b22e',
      itemKey: 'item-4',
      processInstanceKey: '6755399441062831',
      rootProcessInstanceKey: null,
      state: 'COMPLETED',
      processedDate: '2023-08-15T10:42:17.900+0300',
      errorMessage: null,
      operationType: 'MODIFY_PROCESS_INSTANCE',
    },
    {
      batchOperationKey: '35ccdcfc-aeac-4ec8-ac6c-db67e581b22e',
      itemKey: 'item-5',
      processInstanceKey: '6755399441062830',
      rootProcessInstanceKey: null,
      state: 'COMPLETED',
      processedDate: '2023-08-15T10:42:17.600+0300',
      errorMessage: null,
      operationType: 'MODIFY_PROCESS_INSTANCE',
    },
  ],
  page: {
    totalItems: 5,
    startCursor: null,
    endCursor: null,
    hasMoreTotalItems: false,
  },
};

export {
  mockResponses,
  mockBatchOperations,
  mockBatchOperationItems,
  mockBatchOperationItemsWithError,
};
