/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {elementInstanceFilterFieldsSchema} from './gen/zod/elementInstanceFilterFieldsSchema';
import {elementInstanceResultSchema} from './gen/zod/elementInstanceResultSchema';
import {elementInstanceSearchQueryResultSchema} from './gen/zod/elementInstanceSearchQueryResultSchema';
import {elementInstanceSearchQuerySchema} from './gen/zod/elementInstanceSearchQuerySchema';
import {elementInstanceStateEnumSchema} from './gen/zod/elementInstanceStateEnumSchema';
import {getElementInstanceStatus200Schema} from './gen/zod/getElementInstanceSchema';
import {incidentSearchQueryResultSchema} from './gen/zod/incidentSearchQueryResultSchema';
import {incidentSearchQuerySchema} from './gen/zod/incidentSearchQuerySchema';
import {setVariableRequestSchema} from './gen/zod/setVariableRequestSchema';
import type {ElementInstanceResult, ElementInstanceResultTypeEnumKey} from './gen/types/ElementInstanceResult';
import type {ElementInstanceSearchQuery} from './gen/types/ElementInstanceSearchQuery';
import type {ElementInstanceSearchQueryResult} from './gen/types/ElementInstanceSearchQueryResult';
import type {ElementInstanceStateEnumKey} from './gen/types/ElementInstanceStateEnum';
import type {GetElementInstanceStatus200} from './gen/types/GetElementInstance';
import type {IncidentSearchQuery} from './gen/types/IncidentSearchQuery';
import type {IncidentSearchQueryResult} from './gen/types/IncidentSearchQueryResult';
import type {SetVariableRequest} from './gen/types/SetVariableRequest';

const elementInstanceStateSchema = elementInstanceStateEnumSchema;
type ElementInstanceState = ElementInstanceStateEnumKey;

const elementInstanceTypeSchema = elementInstanceResultSchema.shape.type;
type ElementInstanceType = ElementInstanceResultTypeEnumKey;

const elementInstanceSchema = elementInstanceResultSchema;
type ElementInstance = ElementInstanceResult;

// Gen `elementInstanceFilterSchema` includes `$or`. The fields-only filter keeps the meaning of this export.
const elementInstanceFilterSchema = elementInstanceFilterFieldsSchema;

const queryElementInstancesRequestBodySchema = elementInstanceSearchQuerySchema;
type QueryElementInstancesRequestBody = ElementInstanceSearchQuery;

const queryElementInstancesResponseBodySchema = elementInstanceSearchQueryResultSchema;
type QueryElementInstancesResponseBody = ElementInstanceSearchQueryResult;

const getElementInstanceResponseBodySchema = getElementInstanceStatus200Schema;
type GetElementInstanceResponseBody = GetElementInstanceStatus200;

const updateElementInstanceVariablesRequestBodySchema = setVariableRequestSchema;
type UpdateElementInstanceVariablesRequestBody = SetVariableRequest;

const queryElementInstanceIncidentsRequestBodySchema = incidentSearchQuerySchema;
type QueryElementInstanceIncidentsRequestBody = IncidentSearchQuery;

const queryElementInstanceIncidentsResponseBodySchema = incidentSearchQueryResultSchema;
type QueryElementInstanceIncidentsResponseBody = IncidentSearchQueryResult;

const queryElementInstances = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/element-instances/search` as const;
	},
} as const satisfies Endpoint;

const getElementInstance = {
	method: 'GET',
	getUrl(params) {
		const {elementInstanceKey} = params;
		return `/${API_VERSION}/element-instances/${elementInstanceKey}` as const;
	},
} as const satisfies Endpoint<Pick<ElementInstance, 'elementInstanceKey'>>;

const updateElementInstanceVariables = {
	method: 'PUT',
	getUrl(params) {
		const {elementInstanceKey} = params;
		return `/${API_VERSION}/element-instances/${elementInstanceKey}/variables` as const;
	},
} as const satisfies Endpoint<Pick<ElementInstance, 'elementInstanceKey'>>;

const queryElementInstanceIncidents = {
	method: 'POST',
	getUrl: ({elementInstanceKey}) => `/${API_VERSION}/element-instances/${elementInstanceKey}/incidents/search` as const,
} as const satisfies Endpoint<Pick<ElementInstance, 'elementInstanceKey'>>;

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
