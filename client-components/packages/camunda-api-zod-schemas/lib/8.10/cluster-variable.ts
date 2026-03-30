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
	clusterVariableResultSchema,
	clusterVariableScopeEnumSchema,
	clusterVariableSearchResultSchema,
	clusterVariableSearchQueryRequestSchema,
	clusterVariableSearchQueryResultSchema,
	createClusterVariableRequestSchema,
} from './gen';

const clusterVariableScopeSchema = clusterVariableScopeEnumSchema;
type ClusterVariableScope = z.infer<typeof clusterVariableScopeSchema>;

const clusterVariableSchema = clusterVariableResultSchema;
type ClusterVariable = z.infer<typeof clusterVariableSchema>;

const clusterVariableSearchItemSchema = clusterVariableSearchResultSchema;
type ClusterVariableSearchItem = z.infer<typeof clusterVariableSearchItemSchema>;

const createClusterVariableRequestBodySchema = createClusterVariableRequestSchema;
type CreateClusterVariableRequestBody = z.infer<typeof createClusterVariableRequestBodySchema>;

const queryClusterVariablesRequestBodySchema = clusterVariableSearchQueryRequestSchema;
type QueryClusterVariablesRequestBody = z.infer<typeof queryClusterVariablesRequestBodySchema>;

const queryClusterVariablesResponseBodySchema = clusterVariableSearchQueryResultSchema;
type QueryClusterVariablesResponseBody = z.infer<typeof queryClusterVariablesResponseBodySchema>;

const createGlobalClusterVariable: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/cluster-variables/global`,
};

const getGlobalClusterVariable: Endpoint<{name: string}> = {
	method: 'GET',
	getUrl: ({name}) => `/${API_VERSION}/cluster-variables/global/${name}`,
};

const deleteGlobalClusterVariable: Endpoint<{name: string}> = {
	method: 'DELETE',
	getUrl: ({name}) => `/${API_VERSION}/cluster-variables/global/${name}`,
};

const queryClusterVariables: Endpoint<{truncateValues?: boolean} | undefined> = {
	method: 'POST',
	getUrl: (params) =>
		`/${API_VERSION}/cluster-variables/search${params?.truncateValues !== undefined ? `?truncateValues=${params.truncateValues}` : ''}`,
};

const createTenantClusterVariable: Endpoint<{tenantId: string}> = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/cluster-variables/tenants/${tenantId}`,
};

const getTenantClusterVariable: Endpoint<{tenantId: string; name: string}> = {
	method: 'GET',
	getUrl: ({tenantId, name}) => `/${API_VERSION}/cluster-variables/tenants/${tenantId}/${name}`,
};

const deleteTenantClusterVariable: Endpoint<{tenantId: string; name: string}> = {
	method: 'DELETE',
	getUrl: ({tenantId, name}) => `/${API_VERSION}/cluster-variables/tenants/${tenantId}/${name}`,
};

export {
	clusterVariableScopeSchema,
	clusterVariableSchema,
	clusterVariableSearchItemSchema,
	createClusterVariableRequestBodySchema,
	queryClusterVariablesRequestBodySchema,
	queryClusterVariablesResponseBodySchema,
	createGlobalClusterVariable,
	getGlobalClusterVariable,
	deleteGlobalClusterVariable,
	queryClusterVariables,
	createTenantClusterVariable,
	getTenantClusterVariable,
	deleteTenantClusterVariable,
};

export type {
	ClusterVariableScope,
	ClusterVariable,
	ClusterVariableSearchItem,
	CreateClusterVariableRequestBody,
	QueryClusterVariablesRequestBody,
	QueryClusterVariablesResponseBody,
};
