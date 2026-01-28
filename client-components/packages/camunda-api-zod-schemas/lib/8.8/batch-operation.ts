/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {
	API_VERSION,
	basicStringFilterSchema,
	getEnumFilterSchema,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	type Endpoint,
} from '../common';

const batchOperationTypeSchema = z.enum([
	'CANCEL_PROCESS_INSTANCE',
	'RESOLVE_INCIDENT',
	'MIGRATE_PROCESS_INSTANCE',
	'MODIFY_PROCESS_INSTANCE',
	'DELETE_DECISION_DEFINITION',
	'DELETE_PROCESS_DEFINITION',
	'DELETE_PROCESS_INSTANCE',
	'ADD_VARIABLE',
	'UPDATE_VARIABLE',
]);
type BatchOperationType = z.infer<typeof batchOperationTypeSchema>;

const batchOperationStateSchema = z.enum([
	'CREATED',
	'ACTIVE',
	'SUSPENDED',
	'COMPLETED',
	'PARTIALLY_COMPLETED',
	'CANCELED',
	'FAILED',
]);
type BatchOperationState = z.infer<typeof batchOperationStateSchema>;

const batchOperationItemStateSchema = z.enum(['ACTIVE', 'COMPLETED', 'CANCELED', 'FAILED']);
type BatchOperationItemState = z.infer<typeof batchOperationItemStateSchema>;

const batchOperationSchema = z.object({
	batchOperationKey: z.string(),
	state: batchOperationStateSchema,
	batchOperationType: batchOperationTypeSchema,
	startDate: z.string().optional(),
	endDate: z.string().optional(),
	actorType: z.string().optional(),
	actorId: z.string().optional(),
	operationsTotalCount: z.number().int(),
	operationsFailedCount: z.number().int(),
	operationsCompletedCount: z.number().int(),
});
type BatchOperation = z.infer<typeof batchOperationSchema>;

const batchOperationItemSchema = z.object({
	batchOperationKey: z.string(),
	itemKey: z.string(),
	processInstanceKey: z.string(),
	state: batchOperationItemStateSchema,
	processedDate: z.string().optional(),
	errorMessage: z.string().optional(),
	operationType: batchOperationTypeSchema,
});
type BatchOperationItem = z.infer<typeof batchOperationItemSchema>;

const queryBatchOperationsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['batchOperationKey', 'operationType', 'state', 'startDate', 'endDate', 'actorId'] as const,
	filter: z
		.object({
			batchOperationKey: basicStringFilterSchema,
			operationType: getEnumFilterSchema(batchOperationTypeSchema),
			state: getEnumFilterSchema(batchOperationStateSchema),
		})
		.partial(),
});
type QueryBatchOperationsRequestBody = z.infer<typeof queryBatchOperationsRequestBodySchema>;

const queryBatchOperationsResponseBodySchema = getQueryResponseBodySchema(batchOperationSchema);
type QueryBatchOperationsResponseBody = z.infer<typeof queryBatchOperationsResponseBodySchema>;

const queryBatchOperationItemsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['batchOperationKey', 'itemKey', 'processedDate', 'processInstanceKey', 'state'] as const,
	filter: z
		.object({
			batchOperationKey: basicStringFilterSchema,
			itemKey: basicStringFilterSchema,
			processInstanceKey: basicStringFilterSchema,
			state: getEnumFilterSchema(batchOperationItemStateSchema),
			operationType: getEnumFilterSchema(batchOperationTypeSchema),
		})
		.partial(),
});
type QueryBatchOperationItemsRequestBody = z.infer<typeof queryBatchOperationItemsRequestBodySchema>;

const queryBatchOperationItemsResponseBodySchema = getQueryResponseBodySchema(batchOperationItemSchema);
type QueryBatchOperationItemsResponseBody = z.infer<typeof queryBatchOperationItemsResponseBodySchema>;

const getBatchOperation: Endpoint<{batchOperationKey: string}> = {
	method: 'GET',
	getUrl: ({batchOperationKey}) => `/${API_VERSION}/batch-operations/${batchOperationKey}`,
};

const queryBatchOperations: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/batch-operations/search`,
};

const cancelBatchOperation: Endpoint<{batchOperationKey: string}> = {
	method: 'POST',
	getUrl: ({batchOperationKey}) => `/${API_VERSION}/batch-operations/${batchOperationKey}/cancellation`,
};

const suspendBatchOperation: Endpoint<{batchOperationKey: string}> = {
	method: 'POST',
	getUrl: ({batchOperationKey}) => `/${API_VERSION}/batch-operations/${batchOperationKey}/suspension`,
};

const resumeBatchOperation: Endpoint<{batchOperationKey: string}> = {
	method: 'POST',
	getUrl: ({batchOperationKey}) => `/${API_VERSION}/batch-operations/${batchOperationKey}/resumption`,
};

const queryBatchOperationItems: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/batch-operation-items/search`,
};

export {
	batchOperationTypeSchema,
	batchOperationStateSchema,
	batchOperationItemStateSchema,
	batchOperationSchema,
	batchOperationItemSchema,
	queryBatchOperationsRequestBodySchema,
	queryBatchOperationsResponseBodySchema,
	queryBatchOperationItemsRequestBodySchema,
	queryBatchOperationItemsResponseBodySchema,
	getBatchOperation,
	queryBatchOperations,
	cancelBatchOperation,
	suspendBatchOperation,
	resumeBatchOperation,
	queryBatchOperationItems,
};

export type {
	BatchOperationType,
	BatchOperationState,
	BatchOperationItemState,
	BatchOperation,
	BatchOperationItem,
	QueryBatchOperationsRequestBody,
	QueryBatchOperationsResponseBody,
	QueryBatchOperationItemsRequestBody,
	QueryBatchOperationItemsResponseBody,
};
