/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, getQueryRequestBodySchema, getQueryResponseBodySchema, type Endpoint} from '../common';
import {queryGroupsRequestBodySchema, queryGroupsResponseBodySchema, type Group} from './group';
import {queryRolesRequestBodySchema, queryRolesResponseBodySchema, type Role} from './role';
import {
	queryMappingRulesRequestBodySchema,
	queryMappingRulesResponseBodySchema,
	type MappingRule,
} from './mapping-rule';

const tenantSchema = z.object({
	tenantKey: z.string(),
	tenantId: z.string(),
	name: z.string(),
	description: z.string().optional(),
});
type Tenant = z.infer<typeof tenantSchema>;

const createTenantRequestBodySchema = tenantSchema.pick({
	tenantId: true,
	name: true,
	description: true,
});
type CreateTenantRequestBody = z.infer<typeof createTenantRequestBodySchema>;

const createTenantResponseBodySchema = tenantSchema;
type CreateTenantResponseBody = z.infer<typeof createTenantResponseBodySchema>;

const updateTenantRequestBodySchema = tenantSchema.pick({
	name: true,
	description: true,
});
type UpdateTenantRequestBody = z.infer<typeof updateTenantRequestBodySchema>;

const updateTenantResponseBodySchema = tenantSchema;
type UpdateTenantResponseBody = z.infer<typeof updateTenantResponseBodySchema>;

const queryTenantsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['key', 'name', 'tenantId'] as const,
	filter: tenantSchema
		.pick({
			tenantId: true,
			name: true,
		})
		.partial(),
});
type QueryTenantsRequestBody = z.infer<typeof queryTenantsRequestBodySchema>;

const queryTenantsResponseBodySchema = getQueryResponseBodySchema(tenantSchema);
type QueryTenantsResponseBody = z.infer<typeof queryTenantsResponseBodySchema>;

const tenantUserSchema = z.object({
	username: z.string(),
});
type TenantUser = z.infer<typeof tenantUserSchema>;

const queryUsersByTenantRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['username'] as const,
	filter: z.never(),
});
type QueryUsersByTenantRequestBody = z.infer<typeof queryUsersByTenantRequestBodySchema>;

const queryUsersByTenantResponseBodySchema = getQueryResponseBodySchema(tenantUserSchema);
type QueryUsersByTenantResponseBody = z.infer<typeof queryUsersByTenantResponseBodySchema>;

const tenantClientSchema = z.object({
	clientId: z.string(),
});
type TenantClient = z.infer<typeof tenantClientSchema>;

const queryClientsByTenantRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['clientId'] as const,
	filter: z.never(),
});
type QueryClientsByTenantRequestBody = z.infer<typeof queryClientsByTenantRequestBodySchema>;

const queryClientsByTenantResponseBodySchema = getQueryResponseBodySchema(tenantClientSchema);
type QueryClientsByTenantResponseBody = z.infer<typeof queryClientsByTenantResponseBodySchema>;

const queryGroupsByTenantRequestBodySchema = queryGroupsRequestBodySchema;
type QueryGroupsByTenantRequestBody = z.infer<typeof queryGroupsByTenantRequestBodySchema>;

const queryGroupsByTenantResponseBodySchema = queryGroupsResponseBodySchema;
type QueryGroupsByTenantResponseBody = z.infer<typeof queryGroupsByTenantResponseBodySchema>;

const queryRolesByTenantRequestBodySchema = queryRolesRequestBodySchema;
type QueryRolesByTenantRequestBody = z.infer<typeof queryRolesByTenantRequestBodySchema>;

const queryRolesByTenantResponseBodySchema = queryRolesResponseBodySchema;
type QueryRolesByTenantResponseBody = z.infer<typeof queryRolesByTenantResponseBodySchema>;

const queryMappingRulesByTenantRequestBodySchema = queryMappingRulesRequestBodySchema;
type QueryMappingRulesByTenantRequestBody = z.infer<typeof queryMappingRulesByTenantRequestBodySchema>;

const queryMappingRulesByTenantResponseBodySchema = queryMappingRulesResponseBodySchema;
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
