/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {mappingRuleSearchQueryRequestSchema} from './gen/zod/mappingRuleSearchQueryRequestSchema';
import {roleSearchQueryRequestSchema} from './gen/zod/roleSearchQueryRequestSchema';
import {tenantClientResultSchema} from './gen/zod/tenantClientResultSchema';
import {tenantClientSearchQueryRequestSchema} from './gen/zod/tenantClientSearchQueryRequestSchema';
import {tenantClientSearchResultSchema} from './gen/zod/tenantClientSearchResultSchema';
import {tenantCreateRequestSchema} from './gen/zod/tenantCreateRequestSchema';
import {tenantCreateResultSchema} from './gen/zod/tenantCreateResultSchema';
import {tenantGroupSearchQueryRequestSchema} from './gen/zod/tenantGroupSearchQueryRequestSchema';
import {tenantGroupSearchResultSchema} from './gen/zod/tenantGroupSearchResultSchema';
import {tenantMappingRuleSearchResultSchema} from './gen/zod/tenantMappingRuleSearchResultSchema';
import {tenantResultSchema} from './gen/zod/tenantResultSchema';
import {tenantRoleSearchResultSchema} from './gen/zod/tenantRoleSearchResultSchema';
import {tenantSearchQueryRequestSchema} from './gen/zod/tenantSearchQueryRequestSchema';
import {tenantSearchQueryResultSchema} from './gen/zod/tenantSearchQueryResultSchema';
import {tenantUpdateRequestSchema} from './gen/zod/tenantUpdateRequestSchema';
import {tenantUpdateResultSchema} from './gen/zod/tenantUpdateResultSchema';
import {tenantUserResultSchema} from './gen/zod/tenantUserResultSchema';
import {tenantUserSearchQueryRequestSchema} from './gen/zod/tenantUserSearchQueryRequestSchema';
import {tenantUserSearchResultSchema} from './gen/zod/tenantUserSearchResultSchema';
import type {MappingRuleSearchQueryRequest} from './gen/types/MappingRuleSearchQueryRequest';
import type {RoleSearchQueryRequest} from './gen/types/RoleSearchQueryRequest';
import type {TenantClientResult} from './gen/types/TenantClientResult';
import type {TenantClientSearchQueryRequest} from './gen/types/TenantClientSearchQueryRequest';
import type {TenantClientSearchResult} from './gen/types/TenantClientSearchResult';
import type {TenantCreateRequest} from './gen/types/TenantCreateRequest';
import type {TenantCreateResult} from './gen/types/TenantCreateResult';
import type {TenantGroupSearchQueryRequest} from './gen/types/TenantGroupSearchQueryRequest';
import type {TenantGroupSearchResult} from './gen/types/TenantGroupSearchResult';
import type {TenantMappingRuleSearchResult} from './gen/types/TenantMappingRuleSearchResult';
import type {TenantResult} from './gen/types/TenantResult';
import type {TenantRoleSearchResult} from './gen/types/TenantRoleSearchResult';
import type {TenantSearchQueryRequest} from './gen/types/TenantSearchQueryRequest';
import type {TenantSearchQueryResult} from './gen/types/TenantSearchQueryResult';
import type {TenantUpdateRequest} from './gen/types/TenantUpdateRequest';
import type {TenantUpdateResult} from './gen/types/TenantUpdateResult';
import type {TenantUserResult} from './gen/types/TenantUserResult';
import type {TenantUserSearchQueryRequest} from './gen/types/TenantUserSearchQueryRequest';
import type {TenantUserSearchResult} from './gen/types/TenantUserSearchResult';
import {type Group} from './group';
import {type Role} from './role';
import {type MappingRule} from './mapping-rule';

const tenantSchema = tenantResultSchema;
type Tenant = TenantResult;

const createTenantRequestBodySchema = tenantCreateRequestSchema;
type CreateTenantRequestBody = TenantCreateRequest;

const createTenantResponseBodySchema = tenantCreateResultSchema;
type CreateTenantResponseBody = TenantCreateResult;

const updateTenantRequestBodySchema = tenantUpdateRequestSchema;
type UpdateTenantRequestBody = TenantUpdateRequest;

const updateTenantResponseBodySchema = tenantUpdateResultSchema;
type UpdateTenantResponseBody = TenantUpdateResult;

const queryTenantsRequestBodySchema = tenantSearchQueryRequestSchema;
type QueryTenantsRequestBody = TenantSearchQueryRequest;

const queryTenantsResponseBodySchema = tenantSearchQueryResultSchema;
type QueryTenantsResponseBody = TenantSearchQueryResult;

const tenantUserSchema = tenantUserResultSchema;
type TenantUser = TenantUserResult;

const queryUsersByTenantRequestBodySchema = tenantUserSearchQueryRequestSchema;
type QueryUsersByTenantRequestBody = TenantUserSearchQueryRequest;

const queryUsersByTenantResponseBodySchema = tenantUserSearchResultSchema;
type QueryUsersByTenantResponseBody = TenantUserSearchResult;

const tenantClientSchema = tenantClientResultSchema;
type TenantClient = TenantClientResult;

const queryClientsByTenantRequestBodySchema = tenantClientSearchQueryRequestSchema;
type QueryClientsByTenantRequestBody = TenantClientSearchQueryRequest;

const queryClientsByTenantResponseBodySchema = tenantClientSearchResultSchema;
type QueryClientsByTenantResponseBody = TenantClientSearchResult;

const queryGroupsByTenantRequestBodySchema = tenantGroupSearchQueryRequestSchema;
type QueryGroupsByTenantRequestBody = TenantGroupSearchQueryRequest;

const queryGroupsByTenantResponseBodySchema = tenantGroupSearchResultSchema;
type QueryGroupsByTenantResponseBody = TenantGroupSearchResult;

const queryRolesByTenantRequestBodySchema = roleSearchQueryRequestSchema;
type QueryRolesByTenantRequestBody = RoleSearchQueryRequest;

const queryRolesByTenantResponseBodySchema = tenantRoleSearchResultSchema;
type QueryRolesByTenantResponseBody = TenantRoleSearchResult;

const queryMappingRulesByTenantRequestBodySchema = mappingRuleSearchQueryRequestSchema;
type QueryMappingRulesByTenantRequestBody = MappingRuleSearchQueryRequest;

const queryMappingRulesByTenantResponseBodySchema = tenantMappingRuleSearchResultSchema;
type QueryMappingRulesByTenantResponseBody = TenantMappingRuleSearchResult;

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
		`/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/mapping-rules/${encodeURIComponent(mappingRuleId)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'> & Pick<MappingRule, 'mappingRuleId'>>;

const unassignMappingRuleFromTenant = {
	method: 'DELETE',
	getUrl: ({tenantId, mappingRuleId}) =>
		`/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/mapping-rules/${encodeURIComponent(mappingRuleId)}` as const,
} as const satisfies Endpoint<Pick<Tenant, 'tenantId'> & Pick<MappingRule, 'mappingRuleId'>>;

const queryMappingRulesByTenant = {
	method: 'POST',
	getUrl: ({tenantId}) => `/${API_VERSION}/tenants/${encodeURIComponent(tenantId)}/mapping-rules/search` as const,
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
