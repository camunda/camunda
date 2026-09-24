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
	advancedStringFilterSchema,
	getEnumFilterSchema,
	getQueryRequestBodySchema,
	getQueryResponseBodySchema,
	type Endpoint,
} from './common';

const clusterVariableScopeSchema = z.enum(['GLOBAL', 'TENANT']);
type ClusterVariableScope = z.infer<typeof clusterVariableScopeSchema>;

const clusterVariableSchema = z.object({
	name: z.string(),
	scope: clusterVariableScopeSchema,
	tenantId: z.string().nullable(),
	value: z.string(),
});
type ClusterVariable = z.infer<typeof clusterVariableSchema>;

const clusterVariableSearchResultSchema = clusterVariableSchema.extend({
	isTruncated: z.boolean(),
});
type ClusterVariableSearchResult = z.infer<typeof clusterVariableSearchResultSchema>;

const createClusterVariableRequestBodySchema = z.object({
	name: z.string(),
	value: z.unknown(),
});
type CreateClusterVariableRequestBody = z.infer<typeof createClusterVariableRequestBodySchema>;

const updateClusterVariableRequestBodySchema = z.object({
	value: z.unknown(),
});
type UpdateClusterVariableRequestBody = z.infer<typeof updateClusterVariableRequestBodySchema>;

const queryClusterVariablesRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['name', 'value', 'tenantId', 'scope'] as const,
	filter: z.object({
		name: advancedStringFilterSchema.optional(),
		value: advancedStringFilterSchema.optional(),
		scope: getEnumFilterSchema(clusterVariableScopeSchema).optional(),
		tenantId: advancedStringFilterSchema.optional(),
		isTruncated: z.boolean().optional(),
	}),
});
type QueryClusterVariablesRequestBody = z.infer<typeof queryClusterVariablesRequestBodySchema>;

const queryClusterVariablesResponseBodySchema = getQueryResponseBodySchema(clusterVariableSearchResultSchema);
type QueryClusterVariablesResponseBody = z.infer<typeof queryClusterVariablesResponseBodySchema>;

const searchClusterVariables = {
	method: 'POST',
	getUrl: ({truncateValues} = {}) =>
		`/${API_VERSION}/cluster-variables/search${truncateValues !== undefined ? `?truncateValues=${truncateValues}` : ''}` as const,
} as const satisfies Endpoint<{truncateValues?: boolean}>;

const createGlobalClusterVariable = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/cluster-variables/global` as const,
} as const satisfies Endpoint;

const createTenantClusterVariable = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/cluster-variables/tenants/${tenantId}` as const,
} as const satisfies Endpoint<{tenantId: string}>;

const getGlobalClusterVariable = {
	method: 'GET',
	getUrl: ({name}) => `/${API_VERSION}/cluster-variables/global/${name}` as const,
} as const satisfies Endpoint<{name: string}>;

const getTenantClusterVariable = {
	method: 'GET',
	getUrl: ({tenantId, name}) => `/${API_VERSION}/cluster-variables/tenants/${tenantId}/${name}` as const,
} as const satisfies Endpoint<{tenantId: string; name: string}>;

const updateGlobalClusterVariable = {
	method: 'PUT',
	getUrl: ({name}) => `/${API_VERSION}/cluster-variables/global/${name}` as const,
} as const satisfies Endpoint<{name: string}>;

const updateTenantClusterVariable = {
	method: 'PUT',
	getUrl: ({tenantId, name}) => `/${API_VERSION}/cluster-variables/tenants/${tenantId}/${name}` as const,
} as const satisfies Endpoint<{tenantId: string; name: string}>;

const deleteGlobalClusterVariable = {
	method: 'DELETE',
	getUrl: ({name}) => `/${API_VERSION}/cluster-variables/global/${name}` as const,
} as const satisfies Endpoint<{name: string}>;

const deleteTenantClusterVariable = {
	method: 'DELETE',
	getUrl: ({tenantId, name}) => `/${API_VERSION}/cluster-variables/tenants/${tenantId}/${name}` as const,
} as const satisfies Endpoint<{tenantId: string; name: string}>;

export {
	clusterVariableScopeSchema,
	clusterVariableSchema,
	clusterVariableSearchResultSchema,
	createClusterVariableRequestBodySchema,
	updateClusterVariableRequestBodySchema,
	queryClusterVariablesRequestBodySchema,
	queryClusterVariablesResponseBodySchema,
	searchClusterVariables,
	createGlobalClusterVariable,
	createTenantClusterVariable,
	getGlobalClusterVariable,
	getTenantClusterVariable,
	updateGlobalClusterVariable,
	updateTenantClusterVariable,
	deleteGlobalClusterVariable,
	deleteTenantClusterVariable,
};
export type {
	ClusterVariableScope,
	ClusterVariable,
	ClusterVariableSearchResult,
	CreateClusterVariableRequestBody,
	UpdateClusterVariableRequestBody,
	QueryClusterVariablesRequestBody,
	QueryClusterVariablesResponseBody,
};
