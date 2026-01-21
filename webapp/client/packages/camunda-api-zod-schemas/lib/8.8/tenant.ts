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
import type {Group, Role} from './group-role';
import type {MappingRule} from './mapping-rule';

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

const createTenant = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/tenants` as const,
} as const satisfies Endpoint;

const getTenant = {
	method: 'GET',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'>>;

const updateTenant = {
	method: 'PUT',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'>>;

const deleteTenant = {
	method: 'DELETE',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'>>;

const queryTenants = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/tenants/search` as const,
} as const satisfies Endpoint;

const assignUserToTenant = {
	method: 'PUT',
	getUrl: ({tenantId, username}) =>
		`/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/users/${encodeURIComponent(username)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'> & {username: string}>;

const unassignUserFromTenant = {
	method: 'DELETE',
	getUrl: ({tenantId, username}) =>
		`/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/users/${encodeURIComponent(username)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'> & {username: string}>;

const queryUsersByTenant = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/users/search` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'>>;

const queryClientsByTenant = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/clients/search` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'>>;

const queryGroupsByTenant = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/groups/search` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'>>;

const queryRolesByTenant = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/roles/search` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'>>;

const assignClientToTenant = {
	method: 'PUT',
	getUrl: ({tenantId, clientId}) =>
		`/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/clients/${encodeURIComponent(clientId)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'> & {clientId: string}>;

const unassignClientFromTenant = {
	method: 'DELETE',
	getUrl: ({tenantId, clientId}) =>
		`/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/clients/${encodeURIComponent(clientId)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'> & {clientId: string}>;

const assignMappingRuleToTenant = {
	method: 'PUT',
	getUrl: ({tenantId, mappingRuleId}) =>
		`/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/mappings/${encodeURIComponent(mappingRuleId)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'> & Pick<MappingRule, 'mappingRuleId'>>;

const unassignMappingRuleFromTenant = {
	method: 'DELETE',
	getUrl: ({tenantId, mappingRuleId}) =>
		`/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/mappings/${encodeURIComponent(mappingRuleId)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'> & Pick<MappingRule, 'mappingRuleId'>>;

const queryMappingRulesByTenant = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/mappings/search` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'>>;

const assignGroupToTenant = {
	method: 'PUT',
	getUrl: ({tenantId, groupId}) =>
		`/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/groups/${encodeURIComponent(groupId)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'> & Pick<Group, 'groupId'>>;

const unassignGroupFromTenant = {
	method: 'DELETE',
	getUrl: ({tenantId, groupId}) =>
		`/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/groups/${encodeURIComponent(groupId)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'> & Pick<Group, 'groupId'>>;

const assignRoleToTenant = {
	method: 'PUT',
	getUrl: ({tenantId, roleId}) =>
		`/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/roles/${encodeURIComponent(roleId)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'> & Pick<Role, 'roleId'>>;

const unassignRoleFromTenant = {
	method: 'DELETE',
	getUrl: ({tenantId, roleId}) =>
		`/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/roles/${encodeURIComponent(roleId)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'> & Pick<Role, 'roleId'>>;

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
