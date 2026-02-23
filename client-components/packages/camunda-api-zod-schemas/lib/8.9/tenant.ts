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
	queryResponsePageSchema,
	type Endpoint,
} from './common';
import {type Group} from './group';
import {queryRolesRequestBodySchema, type Role} from './role';
import {queryMappingRulesRequestBodySchema, type MappingRule} from './mapping-rule';

const pageOnlyQueryResponseBodySchema = z.object({
	page: queryResponsePageSchema,
});

const tenantSchema = z.object({
	tenantId: z.string(),
	name: z.string(),
	description: z.string().nullable(),
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

const queryGroupsByTenantRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['groupId'] as const,
	filter: z.never(),
});
type QueryGroupsByTenantRequestBody = z.infer<typeof queryGroupsByTenantRequestBodySchema>;

const queryGroupsByTenantResponseBodySchema = getQueryResponseBodySchema(
	z.object({
		groupId: z.string(),
	}),
);
type QueryGroupsByTenantResponseBody = z.infer<typeof queryGroupsByTenantResponseBodySchema>;

const queryRolesByTenantRequestBodySchema = queryRolesRequestBodySchema;
type QueryRolesByTenantRequestBody = z.infer<typeof queryRolesByTenantRequestBodySchema>;

const queryRolesByTenantResponseBodySchema = pageOnlyQueryResponseBodySchema;
type QueryRolesByTenantResponseBody = z.infer<typeof queryRolesByTenantResponseBodySchema>;

const queryMappingRulesByTenantRequestBodySchema = queryMappingRulesRequestBodySchema;
type QueryMappingRulesByTenantRequestBody = z.infer<typeof queryMappingRulesByTenantRequestBodySchema>;

const queryMappingRulesByTenantResponseBodySchema = pageOnlyQueryResponseBodySchema;
type QueryMappingRulesByTenantResponseBody = z.infer<typeof queryMappingRulesByTenantResponseBodySchema>;

const createTenant: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/tenants`,
};

const getTenant: Endpoint<Pick<Tenant, 'tenantId'>> = {
	method: 'GET',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}`,
};

const updateTenant: Endpoint<Pick<Tenant, 'tenantId'>> = {
	method: 'PUT',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}`,
};

const deleteTenant: Endpoint<Pick<Tenant, 'tenantId'>> = {
	method: 'DELETE',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}`,
};

const queryTenants: Endpoint = {
	method: 'POST',
	getUrl: () => `/${API_VERSION}/tenants/search`,
};

const assignUserToTenant: Endpoint<Pick<Tenant, 'tenantId'> & {username: string}> = {
	method: 'PUT',
	getUrl: ({tenantId, username}) => `/${API_VERSION}/tenants/${tenantId}/users/${username}`,
};

const unassignUserFromTenant: Endpoint<Pick<Tenant, 'tenantId'> & {username: string}> = {
	method: 'DELETE',
	getUrl: ({tenantId, username}) => `/${API_VERSION}/tenants/${tenantId}/users/${username}`,
};

const queryUsersByTenant: Endpoint<Pick<Tenant, 'tenantId'>> = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}/users/search`,
};

const queryClientsByTenant: Endpoint<Pick<Tenant, 'tenantId'>> = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}/clients/search`,
};

const queryGroupsByTenant: Endpoint<Pick<Tenant, 'tenantId'>> = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}/groups/search`,
};

const queryRolesByTenant: Endpoint<Pick<Tenant, 'tenantId'>> = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}/roles/search`,
};

const assignClientToTenant: Endpoint<Pick<Tenant, 'tenantId'> & {clientId: string}> = {
	method: 'PUT',
	getUrl: ({tenantId, clientId}) => `/${API_VERSION}/tenants/${tenantId}/clients/${clientId}`,
};

const unassignClientFromTenant: Endpoint<Pick<Tenant, 'tenantId'> & {clientId: string}> = {
	method: 'DELETE',
	getUrl: ({tenantId, clientId}) => `/${API_VERSION}/tenants/${tenantId}/clients/${clientId}`,
};

const assignMappingRuleToTenant: Endpoint<Pick<Tenant, 'tenantId'> & Pick<MappingRule, 'mappingId'>> = {
	method: 'PUT',
	getUrl: ({tenantId, mappingId}) => `/${API_VERSION}/tenants/${tenantId}/mapping-rules/${mappingId}`,
};

const unassignMappingRuleFromTenant: Endpoint<Pick<Tenant, 'tenantId'> & Pick<MappingRule, 'mappingId'>> = {
	method: 'DELETE',
	getUrl: ({tenantId, mappingId}) => `/${API_VERSION}/tenants/${tenantId}/mapping-rules/${mappingId}`,
};

const queryMappingRulesByTenant: Endpoint<Pick<Tenant, 'tenantId'>> = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${tenantId}/mapping-rules/search`,
};

const assignGroupToTenant: Endpoint<Pick<Tenant, 'tenantId'> & Pick<Group, 'groupId'>> = {
	method: 'PUT',
	getUrl: ({tenantId, groupId}) => `/${API_VERSION}/tenants/${tenantId}/groups/${groupId}`,
};

const unassignGroupFromTenant: Endpoint<Pick<Tenant, 'tenantId'> & Pick<Group, 'groupId'>> = {
	method: 'DELETE',
	getUrl: ({tenantId, groupId}) => `/${API_VERSION}/tenants/${tenantId}/groups/${groupId}`,
};

const assignRoleToTenant: Endpoint<Pick<Tenant, 'tenantId'> & Pick<Role, 'roleId'>> = {
	method: 'PUT',
	getUrl: ({tenantId, roleId}) => `/${API_VERSION}/tenants/${tenantId}/roles/${roleId}`,
};

const unassignRoleFromTenant: Endpoint<Pick<Tenant, 'tenantId'> & Pick<Role, 'roleId'>> = {
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
