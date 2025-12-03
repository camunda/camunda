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
} from '../common';
import {evaluatedDecisionInputItemSchema, matchedDecisionRuleItemSchema} from './decision-definition';

const decisionDefinitionTypeSchema = z.enum(['DECISION_TABLE', 'LITERAL_EXPRESSION']);
type DecisionDefinitionType = z.infer<typeof decisionDefinitionTypeSchema>;

const decisionInstanceStateSchema = z.enum(['EVALUATED', 'FAILED']);
type DecisionInstanceState = z.infer<typeof decisionInstanceStateSchema>;

const decisionInstanceSchema = z.object({
	decisionEvaluationInstanceKey: z.string(),
	state: decisionInstanceStateSchema,
	evaluationDate: z.string(),
	evaluationFailure: z.string(),
	decisionDefinitionId: z.string(),
	decisionDefinitionName: z.string(),
	decisionDefinitionVersion: z.number(),
	decisionDefinitionType: decisionDefinitionTypeSchema,
	result: z.string(),
	tenantId: z.string(),
	decisionEvaluationKey: z.string(),
	processDefinitionKey: z.string(),
	processInstanceKey: z.string(),
	decisionDefinitionKey: z.string(),
	elementInstanceKey: z.string(),
	rootDecisionDefinitionKey: z.string(),
});
type DecisionInstance = z.infer<typeof decisionInstanceSchema>;

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
	filter: z
		.object({
			decisionEvaluationInstanceKey: basicStringFilterSchema,
			state: getEnumFilterSchema(decisionInstanceStateSchema),
			evaluationDate: advancedDateTimeFilterSchema,
			decisionDefinitionKey: basicStringFilterSchema,
			elementInstanceKey: basicStringFilterSchema,
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
				rootDecisionDefinitionKey: true,
			}).shape,
		})
		.partial(),
});
type QueryDecisionInstancesRequestBody = z.infer<typeof queryDecisionInstancesRequestBodySchema>;

const queryDecisionInstancesResponseBodySchema = getQueryResponseBodySchema(decisionInstanceSchema);
type QueryDecisionInstancesResponseBody = z.infer<typeof queryDecisionInstancesResponseBodySchema>;

const getDecisionInstanceResponseBodySchema = z.object({
	evaluatedInputs: z.array(evaluatedDecisionInputItemSchema).optional(),
	matchedRules: z.array(matchedDecisionRuleItemSchema).optional(),
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

export {
	decisionDefinitionTypeSchema,
	decisionInstanceStateSchema,
	decisionInstanceSchema,
	queryDecisionInstancesRequestBodySchema,
	queryDecisionInstancesResponseBodySchema,
	getDecisionInstanceResponseBodySchema,
	queryDecisionInstances,
	getDecisionInstance,
};
export type {
	DecisionDefinitionType,
	DecisionInstanceState,
	DecisionInstance,
	QueryDecisionInstancesRequestBody,
	QueryDecisionInstancesResponseBody,
	GetDecisionInstanceResponseBody,
};
