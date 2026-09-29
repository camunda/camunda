/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {batchOperationCreatedResultSchema} from './gen/zod/batchOperationCreatedResultSchema';
import {decisionDefinitionTypeEnumSchema} from './gen/zod/decisionDefinitionTypeEnumSchema';
import {decisionInstanceDeletionBatchOperationRequestSchema} from './gen/zod/decisionInstanceDeletionBatchOperationRequestSchema';
import {decisionInstanceFilterSchema} from './gen/zod/decisionInstanceFilterSchema';
import {decisionInstanceGetQueryResultSchema} from './gen/zod/decisionInstanceGetQueryResultSchema';
import {decisionInstanceResultSchema} from './gen/zod/decisionInstanceResultSchema';
import {decisionInstanceSearchQueryResultSchema} from './gen/zod/decisionInstanceSearchQueryResultSchema';
import {decisionInstanceSearchQuerySchema} from './gen/zod/decisionInstanceSearchQuerySchema';
import {decisionInstanceStateEnumSchema} from './gen/zod/decisionInstanceStateEnumSchema';
import type {BatchOperationCreatedResult} from './gen/types/BatchOperationCreatedResult';
import type {DecisionDefinitionTypeEnumKey} from './gen/types/DecisionDefinitionTypeEnum';
import type {DecisionInstanceDeletionBatchOperationRequest} from './gen/types/DecisionInstanceDeletionBatchOperationRequest';
import type {DecisionInstanceFilter} from './gen/types/DecisionInstanceFilter';
import type {DecisionInstanceGetQueryResult} from './gen/types/DecisionInstanceGetQueryResult';
import type {DecisionInstanceResult} from './gen/types/DecisionInstanceResult';
import type {DecisionInstanceSearchQuery} from './gen/types/DecisionInstanceSearchQuery';
import type {DecisionInstanceSearchQueryResult} from './gen/types/DecisionInstanceSearchQueryResult';
import type {DecisionInstanceStateEnumKey} from './gen/types/DecisionInstanceStateEnum';

const decisionDefinitionTypeSchema = decisionDefinitionTypeEnumSchema;
type DecisionDefinitionType = DecisionDefinitionTypeEnumKey;

const decisionInstanceStateSchema = decisionInstanceStateEnumSchema;
type DecisionInstanceState = DecisionInstanceStateEnumKey;

const decisionInstanceSchema = decisionInstanceResultSchema;
type DecisionInstance = DecisionInstanceResult;

const queryDecisionInstancesFilterSchema = decisionInstanceFilterSchema;
type QueryDecisionInstancesFilter = DecisionInstanceFilter;

const queryDecisionInstancesRequestBodySchema = decisionInstanceSearchQuerySchema;
type QueryDecisionInstancesRequestBody = DecisionInstanceSearchQuery;

const queryDecisionInstancesResponseBodySchema = decisionInstanceSearchQueryResultSchema;
type QueryDecisionInstancesResponseBody = DecisionInstanceSearchQueryResult;

const getDecisionInstanceResponseBodySchema = decisionInstanceGetQueryResultSchema;
type GetDecisionInstanceResponseBody = DecisionInstanceGetQueryResult;

const createDecisionInstancesDeletionBatchOperationRequestBodySchema =
	decisionInstanceDeletionBatchOperationRequestSchema;
type CreateDecisionInstancesDeletionBatchOperationRequestBody = DecisionInstanceDeletionBatchOperationRequest;

const createDecisionInstancesDeletionBatchOperationResponseBodySchema = batchOperationCreatedResultSchema;
type CreateDecisionInstancesDeletionBatchOperationResponseBody = BatchOperationCreatedResult;

const queryDecisionInstances = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/decision-instances/search` as const,
} as const satisfies Endpoint;

const getDecisionInstance = {
	method: 'GET',
	getUrl: ({decisionEvaluationInstanceKey}) =>
		`/${API_VERSION}/decision-instances/${decisionEvaluationInstanceKey}` as const,
} as const satisfies Endpoint<Pick<DecisionInstance, 'decisionEvaluationInstanceKey'>>;

const createDecisionInstancesDeletionBatchOperation = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/decision-instances/deletion` as const,
} as const satisfies Endpoint;

export {
	decisionDefinitionTypeSchema,
	decisionInstanceStateSchema,
	decisionInstanceSchema,
	queryDecisionInstancesFilterSchema,
	queryDecisionInstancesRequestBodySchema,
	queryDecisionInstancesResponseBodySchema,
	getDecisionInstanceResponseBodySchema,
	queryDecisionInstances,
	getDecisionInstance,
	createDecisionInstancesDeletionBatchOperationRequestBodySchema,
	createDecisionInstancesDeletionBatchOperationResponseBodySchema,
	createDecisionInstancesDeletionBatchOperation,
};

export type {
	DecisionDefinitionType,
	DecisionInstanceState,
	DecisionInstance,
	QueryDecisionInstancesFilter,
	QueryDecisionInstancesRequestBody,
	QueryDecisionInstancesResponseBody,
	GetDecisionInstanceResponseBody,
	CreateDecisionInstancesDeletionBatchOperationRequestBody,
	CreateDecisionInstancesDeletionBatchOperationResponseBody,
};
