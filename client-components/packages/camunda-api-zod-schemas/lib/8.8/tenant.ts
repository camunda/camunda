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
	tenantResultSchema,
	tenantCreateRequestSchema,
	tenantUpdateRequestSchema,
	tenantSearchQueryRequestSchema,
	tenantSearchQueryResultSchema,
	tenantUserResultSchema,
	tenantUserSearchQueryRequestSchema,
	tenantUserSearchResultSchema,
	tenantClientResultSchema,
	tenantClientSearchQueryRequestSchema,
	tenantClientSearchResultSchema,
	tenantGroupSearchQueryRequestSchema,
	tenantGroupSearchResultSchema,
	roleSearchQueryRequestSchema,
	roleSearchQueryResultSchema,
	mappingRuleSearchQueryRequestSchema,
	mappingRuleSearchQueryResultSchema,
} from './gen';

const tenantSchema = tenantResultSchema;
type Tenant = z.infer<typeof tenantSchema>;

const createTenantRequestBodySchema = tenantCreateRequestSchema;
type CreateTenantRequestBody = z.infer<typeof createTenantRequestBodySchema>;

const createTenantResponseBodySchema = tenantResultSchema;
type CreateTenantResponseBody = z.infer<typeof createTenantResponseBodySchema>;

const updateTenantRequestBodySchema = tenantUpdateRequestSchema;
type UpdateTenantRequestBody = z.infer<typeof updateTenantRequestBodySchema>;

const updateTenantResponseBodySchema = tenantResultSchema;
type UpdateTenantResponseBody = z.infer<typeof updateTenantResponseBodySchema>;

const queryTenantsRequestBodySchema = tenantSearchQueryRequestSchema;
type QueryTenantsRequestBody = z.infer<typeof queryTenantsRequestBodySchema>;

const queryTenantsResponseBodySchema = tenantSearchQueryResultSchema;
type QueryTenantsResponseBody = z.infer<typeof queryTenantsResponseBodySchema>;

const tenantUserSchema = tenantUserResultSchema;
type TenantUser = z.infer<typeof tenantUserSchema>;

const queryUsersByTenantRequestBodySchema = tenantUserSearchQueryRequestSchema;
type QueryUsersByTenantRequestBody = z.infer<typeof queryUsersByTenantRequestBodySchema>;

const queryUsersByTenantResponseBodySchema = tenantUserSearchResultSchema;
type QueryUsersByTenantResponseBody = z.infer<typeof queryUsersByTenantResponseBodySchema>;

const tenantClientSchema = tenantClientResultSchema;
type TenantClient = z.infer<typeof tenantClientSchema>;

const queryClientsByTenantRequestBodySchema = tenantClientSearchQueryRequestSchema;
type QueryClientsByTenantRequestBody = z.infer<typeof queryClientsByTenantRequestBodySchema>;

const queryClientsByTenantResponseBodySchema = tenantClientSearchResultSchema;
type QueryClientsByTenantResponseBody = z.infer<typeof queryClientsByTenantResponseBodySchema>;

const queryGroupsByTenantRequestBodySchema = tenantGroupSearchQueryRequestSchema;
type QueryGroupsByTenantRequestBody = z.infer<typeof queryGroupsByTenantRequestBodySchema>;

const queryGroupsByTenantResponseBodySchema = tenantGroupSearchResultSchema;
type QueryGroupsByTenantResponseBody = z.infer<typeof queryGroupsByTenantResponseBodySchema>;

const queryRolesByTenantRequestBodySchema = roleSearchQueryRequestSchema;
type QueryRolesByTenantRequestBody = z.infer<typeof queryRolesByTenantRequestBodySchema>;

const queryRolesByTenantResponseBodySchema = roleSearchQueryResultSchema;
type QueryRolesByTenantResponseBody = z.infer<typeof queryRolesByTenantResponseBodySchema>;

const queryMappingRulesByTenantRequestBodySchema = mappingRuleSearchQueryRequestSchema;
type QueryMappingRulesByTenantRequestBody = z.infer<typeof queryMappingRulesByTenantRequestBodySchema>;

const queryMappingRulesByTenantResponseBodySchema = mappingRuleSearchQueryResultSchema;
type QueryMappingRulesByTenantResponseBody = z.infer<typeof queryMappingRulesByTenantResponseBodySchema>;

const createTenant: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/tenants`,
};

const getTenant: Endpoint<{tenantId: string}> = {
	method: 'GET',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}`,
};

const updateTenant: Endpoint<{tenantId: string}> = {
	method: 'PUT',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}`,
};

const deleteTenant: Endpoint<{tenantId: string}> = {
	method: 'DELETE',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}`,
};

const queryTenants: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/tenants/search`,
};

const assignUserToTenant: Endpoint<{tenantId: string; username: string}> = {
	method: 'PUT',
	getUrl: ({tenantId, username}) => `/${API_VERSION}/tenants/${tenantId}/users/${username}`,
};

const unassignUserFromTenant: Endpoint<{tenantId: string; username: string}> = {
	method: 'DELETE',
	getUrl: ({tenantId, username}) => `/${API_VERSION}/tenants/${tenantId}/users/${username}`,
};

const queryUsersByTenant: Endpoint<{tenantId: string}> = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}/users/search`,
};

const queryClientsByTenant: Endpoint<{tenantId: string}> = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}/clients/search`,
};

const queryGroupsByTenant: Endpoint<{tenantId: string}> = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}/groups/search`,
};

const queryRolesByTenant: Endpoint<{tenantId: string}> = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}/roles/search`,
};

const assignClientToTenant: Endpoint<{tenantId: string; clientId: string}> = {
	method: 'PUT',
	getUrl: ({tenantId, clientId}) => `/${API_VERSION}/tenants/${tenantId}/clients/${clientId}`,
};

const unassignClientFromTenant: Endpoint<{tenantId: string; clientId: string}> = {
	method: 'DELETE',
	getUrl: ({tenantId, clientId}) => `/${API_VERSION}/tenants/${tenantId}/clients/${clientId}`,
};

const assignMappingRuleToTenant: Endpoint<{tenantId: string; mappingRuleId: string}> = {
	method: 'PUT',
	getUrl: ({tenantId, mappingRuleId}) => `/${API_VERSION}/tenants/${tenantId}/mappings/${mappingRuleId}`,
};

const unassignMappingRuleFromTenant: Endpoint<{tenantId: string; mappingRuleId: string}> = {
	method: 'DELETE',
	getUrl: ({tenantId, mappingRuleId}) => `/${API_VERSION}/tenants/${tenantId}/mappings/${mappingRuleId}`,
};

const queryMappingRulesByTenant: Endpoint<{tenantId: string}> = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}/mappings/search`,
};

const assignGroupToTenant: Endpoint<{tenantId: string; groupId: string}> = {
	method: 'PUT',
	getUrl: ({tenantId, groupId}) => `/${API_VERSION}/tenants/${tenantId}/groups/${groupId}`,
};

const unassignGroupFromTenant: Endpoint<{tenantId: string; groupId: string}> = {
	method: 'DELETE',
	getUrl: ({tenantId, groupId}) => `/${API_VERSION}/tenants/${tenantId}/groups/${groupId}`,
};

const assignRoleToTenant: Endpoint<{tenantId: string; roleId: string}> = {
	method: 'PUT',
	getUrl: ({tenantId, roleId}) => `/${API_VERSION}/tenants/${tenantId}/roles/${roleId}`,
};

const unassignRoleFromTenant: Endpoint<{tenantId: string; roleId: string}> = {
	method: 'DELETE',
	getUrl: ({tenantId, roleId}) => `/${API_VERSION}/tenants/${tenantId}/roles/${roleId}`,
};

export {
	createTenant,
	getTenant,
	updateTenant,
	deleteTenant,
	queryTenants,
	assignUserToTenant,
	unassignUserFromTenant,
	queryUsersByTenant,
	queryClientsByTenant,
	queryGroupsByTenant,
	queryRolesByTenant,
	assignClientToTenant,
	unassignClientFromTenant,
	assignMappingRuleToTenant,
	unassignMappingRuleFromTenant,
	queryMappingRulesByTenant,
	assignGroupToTenant,
	unassignGroupFromTenant,
	assignRoleToTenant,
	unassignRoleFromTenant,
	tenantSchema,
	createTenantRequestBodySchema,
	createTenantResponseBodySchema,
	updateTenantRequestBodySchema,
	updateTenantResponseBodySchema,
	queryTenantsRequestBodySchema,
	queryTenantsResponseBodySchema,
	tenantUserSchema,
	queryUsersByTenantRequestBodySchema,
	queryUsersByTenantResponseBodySchema,
	tenantClientSchema,
	queryClientsByTenantRequestBodySchema,
	queryClientsByTenantResponseBodySchema,
	queryGroupsByTenantRequestBodySchema,
	queryGroupsByTenantResponseBodySchema,
	queryRolesByTenantRequestBodySchema,
	queryRolesByTenantResponseBodySchema,
	queryMappingRulesByTenantRequestBodySchema,
	queryMappingRulesByTenantResponseBodySchema,
};
export type {
	Tenant,
	CreateTenantRequestBody,
	CreateTenantResponseBody,
	UpdateTenantRequestBody,
	UpdateTenantResponseBody,
	QueryTenantsRequestBody,
	QueryTenantsResponseBody,
	TenantUser,
	QueryUsersByTenantRequestBody,
	QueryUsersByTenantResponseBody,
	TenantClient,
	QueryClientsByTenantRequestBody,
	QueryClientsByTenantResponseBody,
	QueryGroupsByTenantRequestBody,
	QueryGroupsByTenantResponseBody,
	QueryRolesByTenantRequestBody,
	QueryRolesByTenantResponseBody,
	QueryMappingRulesByTenantRequestBody,
	QueryMappingRulesByTenantResponseBody,
};
