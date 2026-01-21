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
	elementInstanceStateEnumSchema,
	elementInstanceResultSchema,
	elementInstanceFilterSchema,
	elementInstanceSearchQuerySchema,
	elementInstanceSearchQueryResultSchema,
	setVariableRequestSchema,
} from './gen';

const elementInstanceStateSchema = elementInstanceStateEnumSchema;
type ElementInstanceState = z.infer<typeof elementInstanceStateSchema>;

const elementInstanceTypeSchema = z.enum([
	'UNSPECIFIED',
	'PROCESS',
	'SUB_PROCESS',
	'EVENT_SUB_PROCESS',
	'AD_HOC_SUB_PROCESS',
	'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
	'START_EVENT',
	'INTERMEDIATE_CATCH_EVENT',
	'INTERMEDIATE_THROW_EVENT',
	'BOUNDARY_EVENT',
	'END_EVENT',
	'SERVICE_TASK',
	'RECEIVE_TASK',
	'USER_TASK',
	'MANUAL_TASK',
	'TASK',
	'EXCLUSIVE_GATEWAY',
	'INCLUSIVE_GATEWAY',
	'PARALLEL_GATEWAY',
	'EVENT_BASED_GATEWAY',
	'SEQUENCE_FLOW',
	'MULTI_INSTANCE_BODY',
	'CALL_ACTIVITY',
	'BUSINESS_RULE_TASK',
	'SCRIPT_TASK',
	'SEND_TASK',
	'UNKNOWN',
]);
type ElementInstanceType = z.infer<typeof elementInstanceTypeSchema>;

const elementInstanceSchema = elementInstanceResultSchema;
type ElementInstance = z.infer<typeof elementInstanceSchema>;

const queryElementInstancesRequestBodySchema = elementInstanceSearchQuerySchema;
type QueryElementInstancesRequestBody = z.infer<typeof queryElementInstancesRequestBodySchema>;

const queryElementInstancesResponseBodySchema = elementInstanceSearchQueryResultSchema;
type QueryElementInstancesResponseBody = z.infer<typeof queryElementInstancesResponseBodySchema>;

const queryElementInstances: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/element-instances/search`;
	},
};

const getElementInstance: Endpoint<{elementInstanceKey: string}> = {
	method: 'GET',
	getUrl(params) {
		const {elementInstanceKey} = params;
		return `/${API_VERSION}/element-instances/${elementInstanceKey}`;
	},
};

const getElementInstanceResponseBodySchema = elementInstanceResultSchema;
type GetElementInstanceResponseBody = z.infer<typeof getElementInstanceResponseBodySchema>;

const updateElementInstanceVariablesRequestBodySchema = setVariableRequestSchema;
type UpdateElementInstanceVariablesRequestBody = z.infer<typeof updateElementInstanceVariablesRequestBodySchema>;

const updateElementInstanceVariables: Endpoint<{elementInstanceKey: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {elementInstanceKey} = params;
		return `/${API_VERSION}/element-instances/${elementInstanceKey}/variables`;
	},
};

const queryElementInstanceIncidents: Endpoint<{elementInstanceKey: string}> = {
	method: 'POST',
	getUrl({elementInstanceKey}) {
		return `/${API_VERSION}/element-instances/${elementInstanceKey}/incidents/search`;
	},
};

export {
	queryElementInstances,
	getElementInstance,
	updateElementInstanceVariables,
	queryElementInstanceIncidents,
	queryElementInstancesRequestBodySchema,
	queryElementInstancesResponseBodySchema,
	getElementInstanceResponseBodySchema,
	updateElementInstanceVariablesRequestBodySchema,
	elementInstanceStateSchema,
	elementInstanceTypeSchema,
	elementInstanceSchema,
	elementInstanceFilterSchema,
};
export type {
	ElementInstanceState,
	ElementInstanceType,
	ElementInstance,
	QueryElementInstancesRequestBody,
	QueryElementInstancesResponseBody,
	GetElementInstanceResponseBody,
	UpdateElementInstanceVariablesRequestBody,
};
