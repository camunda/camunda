/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, type Endpoint} from '../common';
import {
	batchOperationTypeEnumSchema,
	batchOperationStateEnumSchema,
	batchOperationItemStateEnumSchema,
	batchOperationResponseSchema,
	batchOperationItemResponseSchema,
	batchOperationSearchQuerySchema,
	batchOperationSearchQueryResultSchema,
	batchOperationItemSearchQuerySchema,
	batchOperationItemSearchQueryResultSchema,
} from './gen';

const batchOperationTypeSchema = batchOperationTypeEnumSchema;
type BatchOperationType = z.infer<typeof batchOperationTypeSchema>;

const batchOperationStateSchema = batchOperationStateEnumSchema;
type BatchOperationState = z.infer<typeof batchOperationStateSchema>;

const batchOperationItemStateSchema = batchOperationItemStateEnumSchema;
type BatchOperationItemState = z.infer<typeof batchOperationItemStateSchema>;

const batchOperationSchema = batchOperationResponseSchema;
type BatchOperation = z.infer<typeof batchOperationSchema>;

const batchOperationItemSchema = batchOperationItemResponseSchema;
type BatchOperationItem = z.infer<typeof batchOperationItemSchema>;

const queryBatchOperationsRequestBodySchema = batchOperationSearchQuerySchema;
type QueryBatchOperationsRequestBody = z.infer<typeof queryBatchOperationsRequestBodySchema>;

const queryBatchOperationsResponseBodySchema = batchOperationSearchQueryResultSchema;
type QueryBatchOperationsResponseBody = z.infer<typeof queryBatchOperationsResponseBodySchema>;

const queryBatchOperationItemsRequestBodySchema = batchOperationItemSearchQuerySchema;
type QueryBatchOperationItemsRequestBody = z.infer<typeof queryBatchOperationItemsRequestBodySchema>;

const queryBatchOperationItemsResponseBodySchema = batchOperationItemSearchQueryResultSchema;
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
