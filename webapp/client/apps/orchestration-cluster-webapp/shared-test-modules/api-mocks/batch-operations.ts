/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BatchOperation, QueryBatchOperationsResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';

function createBatchOperation(overrides?: Partial<BatchOperation>): BatchOperation {
	return {
		batchOperationKey: 'batch-op-1',
		state: 'COMPLETED',
		batchOperationType: 'CANCEL_PROCESS_INSTANCE',
		startDate: '2024-01-01T10:00:00.000Z',
		endDate: '2024-01-01T10:05:00.000Z',
		actorType: 'USER',
		actorId: 'demo',
		operationsTotalCount: 10,
		operationsCompletedCount: 10,
		operationsFailedCount: 0,
		errors: [],
		...overrides,
	};
}

function createQueryBatchOperationsResponse(
	overrides?: Partial<QueryBatchOperationsResponseBody>,
): QueryBatchOperationsResponseBody {
	return {
		items: [],
		page: {totalItems: 0, startCursor: null, endCursor: null, hasMoreTotalItems: false},
		...overrides,
	};
}

export {createBatchOperation, createQueryBatchOperationsResponse};
