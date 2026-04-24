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
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	advancedDateTimeFilterSchema,
	basicStringFilterSchema,
	type Endpoint,
	getEnumFilterSchema,
} from './common';
import {evaluatedDecisionInputItemSchema, matchedDecisionRuleItemSchema} from './decision-definition';
import {batchOperationTypeSchema} from './batch-operation';

const decisionDefinitionTypeSchema = z.enum(['DECISION_TABLE', 'LITERAL_EXPRESSION', 'UNSPECIFIED', 'UNKNOWN']);
type DecisionDefinitionType = z.infer<typeof decisionDefinitionTypeSchema>;

const decisionInstanceStateSchema = z.enum(['EVALUATED', 'FAILED', 'UNSPECIFIED', 'UNKNOWN']);
type DecisionInstanceState = z.infer<typeof decisionInstanceStateSchema>;

const decisionInstanceSchema = z.object({
	decisionEvaluationInstanceKey: z.string(),
	state: decisionInstanceStateSchema,
	evaluationDate: z.string(),
	evaluationFailure: z.string().nullable(),
	decisionDefinitionId: z.string(),
	decisionDefinitionName: z.string(),
	decisionDefinitionVersion: z.number(),
	decisionDefinitionType: decisionDefinitionTypeSchema,
	result: z.string(),
	tenantId: z.string(),
	decisionEvaluationKey: z.string(),
	processDefinitionKey: z.string(),
	processInstanceKey: z.string(),
	rootProcessInstanceKey: z.string().nullable(),
	decisionDefinitionKey: z.string(),
	elementInstanceKey: z.string(),
	rootDecisionDefinitionKey: z.string(),
});
type DecisionInstance = z.infer<typeof decisionInstanceSchema>;

const queryDecisionInstancesFilterSchema = z
	.object({
		decisionEvaluationInstanceKey: basicStringFilterSchema,
		state: getEnumFilterSchema(decisionInstanceStateSchema),
		evaluationDate: advancedDateTimeFilterSchema,
		decisionDefinitionKey: basicStringFilterSchema,
		elementInstanceKey: basicStringFilterSchema,
		rootDecisionDefinitionKey: basicStringFilterSchema,
		decisionRequirementsKey: basicStringFilterSchema,
		...decisionInstanceSchema.pick({
			evaluationFailure: true,
			decisionDefinitionId: true,
			decisionDefinitionName: true,
			decisionDefinitionVersion: true,
			decisionDefinitionType: true,
			tenantId: true,
			decisionEvaluationKey: true,
			processDefinitionKey: true,
			processInstanceKey: true,
		}).shape,
	})
	.partial();
type QueryDecisionInstancesFilter = z.infer<typeof queryDecisionInstancesFilterSchema>;

const queryDecisionInstancesRequestBodySchema = getQueryRequestBodySchema({
	sortFields: [
		'decisionEvaluationKey',
		'decisionEvaluationInstanceKey',
		'state',
		'evaluationDate',
		'evaluationFailure',
		'processDefinitionKey',
		'processInstanceKey',
		'decisionDefinitionKey',
		'decisionDefinitionId',
		'decisionDefinitionName',
		'decisionDefinitionVersion',
		'decisionDefinitionType',
		'tenantId',
		'elementInstanceKey',
		'rootDecisionDefinitionKey',
	] as const,
	filter: queryDecisionInstancesFilterSchema,
});
type QueryDecisionInstancesRequestBody = z.infer<typeof queryDecisionInstancesRequestBodySchema>;

const queryDecisionInstancesResponseBodySchema = getQueryResponseBodySchema(decisionInstanceSchema);
type QueryDecisionInstancesResponseBody = z.infer<typeof queryDecisionInstancesResponseBodySchema>;

const getDecisionInstanceResponseBodySchema = z.object({
	evaluatedInputs: z.array(evaluatedDecisionInputItemSchema),
	matchedRules: z.array(matchedDecisionRuleItemSchema),
	...decisionInstanceSchema.shape,
});
type GetDecisionInstanceResponseBody = z.infer<typeof getDecisionInstanceResponseBodySchema>;

const queryDecisionInstances: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/decision-instances/search`,
};

const getDecisionInstance: Endpoint<Pick<DecisionInstance, 'decisionEvaluationInstanceKey'>> = {
	method: 'GET',
	getUrl: ({decisionEvaluationInstanceKey}) => `/${API_VERSION}/decision-instances/${decisionEvaluationInstanceKey}`,
};

const createDecisionInstancesDeletionBatchOperationRequestBodySchema = z.object({
	filter: queryDecisionInstancesFilterSchema,
});
type CreateDecisionInstancesDeletionBatchOperationRequestBody = z.infer<
	typeof createDecisionInstancesDeletionBatchOperationRequestBodySchema
>;

const createDecisionInstancesDeletionBatchOperationResponseBodySchema = z.object({
	batchOperationKey: z.string(),
	batchOperationType: batchOperationTypeSchema,
});
type CreateDecisionInstancesDeletionBatchOperationResponseBody = z.infer<
	typeof createDecisionInstancesDeletionBatchOperationResponseBodySchema
>;

const createDecisionInstancesDeletionBatchOperation: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/decision-instances/deletion`,
};

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
