/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {clusterVariableResultSchema} from './gen/zod/clusterVariableResultSchema';
import {clusterVariableScopeEnumSchema} from './gen/zod/clusterVariableScopeEnumSchema';
import {clusterVariableSearchQueryRequestSchema} from './gen/zod/clusterVariableSearchQueryRequestSchema';
import {clusterVariableSearchQueryResultSchema} from './gen/zod/clusterVariableSearchQueryResultSchema';
import {clusterVariableSearchResultSchema} from './gen/zod/clusterVariableSearchResultSchema';
import {createClusterVariableRequestSchema} from './gen/zod/createClusterVariableRequestSchema';
import {updateClusterVariableRequestSchema} from './gen/zod/updateClusterVariableRequestSchema';
import type {ClusterVariableResult} from './gen/types/ClusterVariableResult';
import type {ClusterVariableScopeEnumKey} from './gen/types/ClusterVariableScopeEnum';
import type {ClusterVariableSearchQueryRequest} from './gen/types/ClusterVariableSearchQueryRequest';
import type {ClusterVariableSearchQueryResult} from './gen/types/ClusterVariableSearchQueryResult';
import type {ClusterVariableSearchResult} from './gen/types/ClusterVariableSearchResult';
import type {CreateClusterVariableRequest} from './gen/types/CreateClusterVariableRequest';
import type {UpdateClusterVariableRequest} from './gen/types/UpdateClusterVariableRequest';

const clusterVariableScopeSchema = clusterVariableScopeEnumSchema;
type ClusterVariableScope = ClusterVariableScopeEnumKey;

const clusterVariableSchema = clusterVariableResultSchema;
type ClusterVariable = ClusterVariableResult;

const createClusterVariableRequestBodySchema = createClusterVariableRequestSchema;
type CreateClusterVariableRequestBody = CreateClusterVariableRequest;

const updateClusterVariableRequestBodySchema = updateClusterVariableRequestSchema;
type UpdateClusterVariableRequestBody = UpdateClusterVariableRequest;

const queryClusterVariablesRequestBodySchema = clusterVariableSearchQueryRequestSchema;
type QueryClusterVariablesRequestBody = ClusterVariableSearchQueryRequest;

const queryClusterVariablesResponseBodySchema = clusterVariableSearchQueryResultSchema;
type QueryClusterVariablesResponseBody = ClusterVariableSearchQueryResult;

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
