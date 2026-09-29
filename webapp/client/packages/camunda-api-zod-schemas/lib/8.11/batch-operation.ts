/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {batchOperationErrorSchema as genBatchOperationErrorSchema} from './gen/zod/batchOperationErrorSchema';
import {batchOperationItemResponseSchema} from './gen/zod/batchOperationItemResponseSchema';
import {batchOperationItemSearchQueryResultSchema} from './gen/zod/batchOperationItemSearchQueryResultSchema';
import {batchOperationItemSearchQuerySchema} from './gen/zod/batchOperationItemSearchQuerySchema';
import {batchOperationResponseSchema} from './gen/zod/batchOperationResponseSchema';
import {batchOperationSearchQueryResultSchema} from './gen/zod/batchOperationSearchQueryResultSchema';
import {batchOperationSearchQuerySchema} from './gen/zod/batchOperationSearchQuerySchema';
import {batchOperationStateEnumSchema} from './gen/zod/batchOperationStateEnumSchema';
import {batchOperationTypeEnumSchema} from './gen/zod/batchOperationTypeEnumSchema';
import type {
	BatchOperationError as GenBatchOperationError,
	BatchOperationErrorTypeEnumKey,
} from './gen/types/BatchOperationError';
import type {
	BatchOperationItemResponse,
	BatchOperationItemResponseStateEnumKey,
} from './gen/types/BatchOperationItemResponse';
import type {BatchOperationItemSearchQuery} from './gen/types/BatchOperationItemSearchQuery';
import type {BatchOperationItemSearchQueryResult} from './gen/types/BatchOperationItemSearchQueryResult';
import type {BatchOperationResponse} from './gen/types/BatchOperationResponse';
import type {BatchOperationSearchQuery} from './gen/types/BatchOperationSearchQuery';
import type {BatchOperationSearchQueryResult} from './gen/types/BatchOperationSearchQueryResult';
import type {BatchOperationStateEnumKey} from './gen/types/BatchOperationStateEnum';
import type {BatchOperationTypeEnumKey} from './gen/types/BatchOperationTypeEnum';

const batchOperationTypeSchema = batchOperationTypeEnumSchema;
type BatchOperationType = BatchOperationTypeEnumKey;

const batchOperationStateSchema = batchOperationStateEnumSchema;
type BatchOperationState = BatchOperationStateEnumKey;

// The standalone gen `batchOperationItemStateEnumSchema` has no `SKIPPED`. The item response inlines the full enum.
const batchOperationItemStateSchema = batchOperationItemResponseSchema.shape.state;
type BatchOperationItemState = BatchOperationItemResponseStateEnumKey;

const batchOperationErrorTypeSchema = genBatchOperationErrorSchema.shape.type;
type BatchOperationErrorType = BatchOperationErrorTypeEnumKey;

const batchOperationErrorSchema = genBatchOperationErrorSchema;
type BatchOperationError = GenBatchOperationError;

const batchOperationSchema = batchOperationResponseSchema;
type BatchOperation = BatchOperationResponse;

const batchOperationItemSchema = batchOperationItemResponseSchema;
type BatchOperationItem = BatchOperationItemResponse;

const queryBatchOperationsRequestBodySchema = batchOperationSearchQuerySchema;
type QueryBatchOperationsRequestBody = BatchOperationSearchQuery;

const queryBatchOperationsResponseBodySchema = batchOperationSearchQueryResultSchema;
type QueryBatchOperationsResponseBody = BatchOperationSearchQueryResult;

const queryBatchOperationItemsRequestBodySchema = batchOperationItemSearchQuerySchema;
type QueryBatchOperationItemsRequestBody = BatchOperationItemSearchQuery;

const queryBatchOperationItemsResponseBodySchema = batchOperationItemSearchQueryResultSchema;
type QueryBatchOperationItemsResponseBody = BatchOperationItemSearchQueryResult;

const getBatchOperation = {
	method: 'GET',
	getUrl: ({batchOperationKey}) => `/${API_VERSION}/batch-operations/${batchOperationKey}` as const,
} as const satisfies Endpoint<{batchOperationKey: string}>;

const queryBatchOperations = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/batch-operations/search` as const,
} as const satisfies Endpoint;

const cancelBatchOperation = {
	method: 'POST',
	getUrl: ({batchOperationKey}) => `/${API_VERSION}/batch-operations/${batchOperationKey}/cancellation` as const,
} as const satisfies Endpoint<{batchOperationKey: string}>;

const suspendBatchOperation = {
	method: 'POST',
	getUrl: ({batchOperationKey}) => `/${API_VERSION}/batch-operations/${batchOperationKey}/suspension` as const,
} as const satisfies Endpoint<{batchOperationKey: string}>;

const resumeBatchOperation = {
	method: 'POST',
	getUrl: ({batchOperationKey}) => `/${API_VERSION}/batch-operations/${batchOperationKey}/resumption` as const,
} as const satisfies Endpoint<{batchOperationKey: string}>;

const queryBatchOperationItems = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/batch-operation-items/search` as const,
} as const satisfies Endpoint;

export {
	batchOperationTypeSchema,
	batchOperationStateSchema,
	batchOperationItemStateSchema,
	batchOperationErrorTypeSchema,
	batchOperationErrorSchema,
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
	BatchOperationErrorType,
	BatchOperationError,
	BatchOperation,
	BatchOperationItem,
	QueryBatchOperationsRequestBody,
	QueryBatchOperationsResponseBody,
	QueryBatchOperationItemsRequestBody,
	QueryBatchOperationItemsResponseBody,
};
