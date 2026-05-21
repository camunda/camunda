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
	getEnumFilterSchema,
	type Endpoint,
	advancedDateTimeFilterSchema,
} from './common';
import {queryIncidentsRequestBodySchema, queryIncidentsResponseBodySchema} from './incident';

const elementInstanceStateSchema = z.enum(['ACTIVE', 'COMPLETED', 'TERMINATED']);
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

const elementInstanceSchema = z.object({
	processDefinitionId: z.string(),
	startDate: z.string(),
	endDate: z.string().nullable(),
	elementId: z.string(),
	elementName: z.string().nullable(),
	type: elementInstanceTypeSchema,
	state: elementInstanceStateSchema,
	hasIncident: z.boolean(),
	tenantId: z.string(),
	elementInstanceKey: z.string(),
	processInstanceKey: z.string(),
	rootProcessInstanceKey: z.string().nullable(),
	processDefinitionKey: z.string(),
	incidentKey: z.string().nullable(),
});
type ElementInstance = z.infer<typeof elementInstanceSchema>;

const elementInstanceFilterSchema = z
	.object({
		processDefinitionId: z.string(),
		state: z.union([elementInstanceStateSchema, getEnumFilterSchema(elementInstanceStateSchema)]),
		type: elementInstanceTypeSchema,
		elementId: z.string(),
		elementName: z.string(),
		hasIncident: z.boolean(),
		tenantId: z.string(),
		elementInstanceKey: z.string(),
		processInstanceKey: z.string(),
		processDefinitionKey: z.string(),
		incidentKey: z.string(),
		startDate: advancedDateTimeFilterSchema,
		endDate: advancedDateTimeFilterSchema,
		elementInstanceScopeKey: z.string(),
	})
	.partial();

const queryElementInstancesRequestBodySchema = getQueryRequestBodySchema({
	sortFields: [
		'elementInstanceKey',
		'processInstanceKey',
		'processDefinitionKey',
		'processDefinitionId',
		'startDate',
		'endDate',
		'elementId',
		'elementName',
		'type',
		'state',
		'incidentKey',
		'tenantId',
	] as const,
	filter: elementInstanceFilterSchema,
});
type QueryElementInstancesRequestBody = z.infer<typeof queryElementInstancesRequestBodySchema>;

const queryElementInstancesResponseBodySchema = getQueryResponseBodySchema(elementInstanceSchema);
type QueryElementInstancesResponseBody = z.infer<typeof queryElementInstancesResponseBodySchema>;

const queryElementInstances: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/element-instances/search`;
	},
};

const getElementInstance: Endpoint<Pick<ElementInstance, 'elementInstanceKey'>> = {
	method: 'GET',
	getUrl(params) {
		const {elementInstanceKey} = params;
		return `/${API_VERSION}/element-instances/${elementInstanceKey}`;
	},
};

const getElementInstanceResponseBodySchema = elementInstanceSchema;
type GetElementInstanceResponseBody = z.infer<typeof getElementInstanceResponseBodySchema>;

const updateElementInstanceVariablesRequestBodySchema = z.object({
	variables: z.record(z.string(), z.unknown()),
	local: z.boolean().optional(),
});
type UpdateElementInstanceVariablesRequestBody = z.infer<typeof updateElementInstanceVariablesRequestBodySchema>;

const updateElementInstanceVariables: Endpoint<Pick<ElementInstance, 'elementInstanceKey'>> = {
	method: 'PUT',
	getUrl(params) {
		const {elementInstanceKey} = params;
		return `/${API_VERSION}/element-instances/${elementInstanceKey}/variables`;
	},
};

const queryElementInstanceIncidentsRequestBodySchema = queryIncidentsRequestBodySchema;
type QueryElementInstanceIncidentsRequestBody = z.infer<typeof queryElementInstanceIncidentsRequestBodySchema>;

const queryElementInstanceIncidentsResponseBodySchema = queryIncidentsResponseBodySchema;
type QueryElementInstanceIncidentsResponseBody = z.infer<typeof queryElementInstanceIncidentsResponseBodySchema>;

const queryElementInstanceIncidents: Endpoint<Pick<ElementInstance, 'elementInstanceKey'>> = {
	method: 'POST',
	getUrl: ({elementInstanceKey}) => `/${API_VERSION}/element-instances/${elementInstanceKey}/incidents/search`,
};

export {
	queryElementInstances,
	getElementInstance,
	updateElementInstanceVariables,
	queryElementInstancesRequestBodySchema,
	queryElementInstancesResponseBodySchema,
	getElementInstanceResponseBodySchema,
	updateElementInstanceVariablesRequestBodySchema,
	elementInstanceStateSchema,
	elementInstanceTypeSchema,
	elementInstanceSchema,
	elementInstanceFilterSchema,
	queryElementInstanceIncidentsRequestBodySchema,
	queryElementInstanceIncidentsResponseBodySchema,
	queryElementInstanceIncidents,
};
export type {
	ElementInstanceState,
	ElementInstanceType,
	ElementInstance,
	QueryElementInstancesRequestBody,
	QueryElementInstancesResponseBody,
	GetElementInstanceResponseBody,
	UpdateElementInstanceVariablesRequestBody,
	QueryElementInstanceIncidentsRequestBody,
	QueryElementInstanceIncidentsResponseBody,
};
