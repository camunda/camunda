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
	decisionDefinitionTypeEnumSchema,
	decisionInstanceStateEnumSchema,
	decisionInstanceResultSchema,
	decisionInstanceSearchQuerySchema,
	decisionInstanceSearchQueryResultSchema,
	decisionInstanceGetQueryResultSchema,
} from './gen';

const decisionDefinitionTypeSchema = decisionDefinitionTypeEnumSchema;
type DecisionDefinitionType = z.infer<typeof decisionDefinitionTypeSchema>;

const decisionInstanceStateSchema = decisionInstanceStateEnumSchema;
type DecisionInstanceState = z.infer<typeof decisionInstanceStateSchema>;

const decisionInstanceSchema = decisionInstanceResultSchema;
type DecisionInstance = z.infer<typeof decisionInstanceSchema>;

const queryDecisionInstancesRequestBodySchema = decisionInstanceSearchQuerySchema;
type QueryDecisionInstancesRequestBody = z.infer<typeof queryDecisionInstancesRequestBodySchema>;

const queryDecisionInstancesResponseBodySchema = decisionInstanceSearchQueryResultSchema;
type QueryDecisionInstancesResponseBody = z.infer<typeof queryDecisionInstancesResponseBodySchema>;

const getDecisionInstanceResponseBodySchema = decisionInstanceGetQueryResultSchema;
type GetDecisionInstanceResponseBody = z.infer<typeof getDecisionInstanceResponseBodySchema>;

const queryDecisionInstances = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/decision-instances/search` as const,
} as const satisfies Endpoint;

const getDecisionInstance = {
	method: 'GET',
	getUrl: ({decisionEvaluationInstanceKey}) =>
		`/${API_VERSION}/decision-instances/${decisionEvaluationInstanceKey}` as const,
} as const satisfies Endpoint<Pick<DecisionInstance, 'decisionEvaluationInstanceKey'>>;

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
