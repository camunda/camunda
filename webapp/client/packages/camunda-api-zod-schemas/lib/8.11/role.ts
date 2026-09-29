/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {mappingRuleSearchQueryRequestSchema} from './gen/zod/mappingRuleSearchQueryRequestSchema';
import {roleClientSearchQueryRequestSchema} from './gen/zod/roleClientSearchQueryRequestSchema';
import {roleClientSearchResultSchema} from './gen/zod/roleClientSearchResultSchema';
import {roleCreateRequestSchema} from './gen/zod/roleCreateRequestSchema';
import {roleCreateResultSchema} from './gen/zod/roleCreateResultSchema';
import {roleGroupSearchQueryRequestSchema} from './gen/zod/roleGroupSearchQueryRequestSchema';
import {roleGroupSearchResultSchema} from './gen/zod/roleGroupSearchResultSchema';
import {roleMappingRuleSearchResultSchema} from './gen/zod/roleMappingRuleSearchResultSchema';
import {roleSearchQueryRequestSchema} from './gen/zod/roleSearchQueryRequestSchema';
import {roleSearchQueryResultSchema} from './gen/zod/roleSearchQueryResultSchema';
import {roleUpdateRequestSchema} from './gen/zod/roleUpdateRequestSchema';
import {roleUpdateResultSchema} from './gen/zod/roleUpdateResultSchema';
import {roleUserSearchQueryRequestSchema} from './gen/zod/roleUserSearchQueryRequestSchema';
import {roleUserSearchResultSchema} from './gen/zod/roleUserSearchResultSchema';
import type {MappingRuleSearchQueryRequest} from './gen/types/MappingRuleSearchQueryRequest';
import type {RoleClientSearchQueryRequest} from './gen/types/RoleClientSearchQueryRequest';
import type {RoleClientSearchResult} from './gen/types/RoleClientSearchResult';
import type {RoleCreateRequest} from './gen/types/RoleCreateRequest';
import type {RoleCreateResult} from './gen/types/RoleCreateResult';
import type {RoleGroupSearchQueryRequest} from './gen/types/RoleGroupSearchQueryRequest';
import type {RoleGroupSearchResult} from './gen/types/RoleGroupSearchResult';
import type {RoleMappingRuleSearchResult} from './gen/types/RoleMappingRuleSearchResult';
import type {RoleSearchQueryRequest} from './gen/types/RoleSearchQueryRequest';
import type {RoleSearchQueryResult} from './gen/types/RoleSearchQueryResult';
import type {RoleUpdateRequest} from './gen/types/RoleUpdateRequest';
import type {RoleUpdateResult} from './gen/types/RoleUpdateResult';
import type {RoleUserSearchQueryRequest} from './gen/types/RoleUserSearchQueryRequest';
import type {RoleUserSearchResult} from './gen/types/RoleUserSearchResult';
import {roleSchema, type Group, type Role} from './group-role';
import type {MappingRule} from './mapping-rule';

const createRoleRequestBodySchema = roleCreateRequestSchema;
type CreateRoleRequestBody = RoleCreateRequest;

const createRoleResponseBodySchema = roleCreateResultSchema;
type CreateRoleResponseBody = RoleCreateResult;

const updateRoleRequestBodySchema = roleUpdateRequestSchema;
type UpdateRoleRequestBody = RoleUpdateRequest;

const updateRoleResponseBodySchema = roleUpdateResultSchema;
type UpdateRoleResponseBody = RoleUpdateResult;

const queryRolesRequestBodySchema = roleSearchQueryRequestSchema;
type QueryRolesRequestBody = RoleSearchQueryRequest;

const queryRolesResponseBodySchema = roleSearchQueryResultSchema;
type QueryRolesResponseBody = RoleSearchQueryResult;

const queryUsersByRoleRequestBodySchema = roleUserSearchQueryRequestSchema;
type QueryUsersByRoleRequestBody = RoleUserSearchQueryRequest;

const queryUsersByRoleResponseBodySchema = roleUserSearchResultSchema;
type QueryUsersByRoleResponseBody = RoleUserSearchResult;

const queryClientsByRoleRequestBodySchema = roleClientSearchQueryRequestSchema;
type QueryClientsByRoleRequestBody = RoleClientSearchQueryRequest;

const queryClientsByRoleResponseBodySchema = roleClientSearchResultSchema;
type QueryClientsByRoleResponseBody = RoleClientSearchResult;

const queryGroupsByRoleRequestBodySchema = roleGroupSearchQueryRequestSchema;
type QueryGroupsByRoleRequestBody = RoleGroupSearchQueryRequest;

const queryGroupsByRoleResponseBodySchema = roleGroupSearchResultSchema;
type QueryGroupsByRoleResponseBody = RoleGroupSearchResult;

const queryMappingRulesByRoleRequestBodySchema = mappingRuleSearchQueryRequestSchema;
type QueryMappingRulesByRoleRequestBody = MappingRuleSearchQueryRequest;

const queryMappingRulesByRoleResponseBodySchema = roleMappingRuleSearchResultSchema;
type QueryMappingRulesByRoleResponseBody = RoleMappingRuleSearchResult;

const createRole = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/roles` as const;
	},
} as const satisfies Endpoint;

const getRole = {
	method: 'GET',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'>>;

const updateRole = {
	method: 'PUT',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'>>;

const deleteRole = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'>>;

const queryRoles = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/roles/search` as const;
	},
} as const satisfies Endpoint;

const queryUsersByRole = {
	method: 'POST',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}/users/search` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'>>;

const queryClientsByRole = {
	method: 'POST',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}/clients/search` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'>>;

const assignUserToRole = {
	method: 'PUT',
	getUrl(params) {
		const {roleId, username} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}/users/${encodeURIComponent(username)}` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'> & {username: string}>;

const unassignUserFromRole = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId, username} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}/users/${encodeURIComponent(username)}` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'> & {username: string}>;

const assignClientToRole = {
	method: 'PUT',
	getUrl(params) {
		const {roleId, clientId} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}/clients/${encodeURIComponent(clientId)}` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'> & {clientId: string}>;

const unassignClientFromRole = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId, clientId} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}/clients/${encodeURIComponent(clientId)}` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'> & {clientId: string}>;

const assignGroupToRole = {
	method: 'PUT',
	getUrl(params) {
		const {roleId, groupId} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}/groups/${encodeURIComponent(groupId)}` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'> & Pick<Group, 'groupId'>>;

const unassignGroupFromRole = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId, groupId} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}/groups/${encodeURIComponent(groupId)}` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'> & Pick<Group, 'groupId'>>;

const queryGroupsByRole = {
	method: 'POST',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}/groups/search` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'>>;

const assignMappingToRole = {
	method: 'PUT',
	getUrl(params) {
		const {roleId, mappingRuleId} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}/mapping-rules/${encodeURIComponent(mappingRuleId)}` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'> & Pick<MappingRule, 'mappingRuleId'>>;

const unassignMappingFromRole = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId, mappingRuleId} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}/mapping-rules/${encodeURIComponent(mappingRuleId)}` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'> & Pick<MappingRule, 'mappingRuleId'>>;

const queryMappingRulesByRole = {
	method: 'POST',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}/mapping-rules/search` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'>>;

export {
	createRole,
	getRole,
	updateRole,
	deleteRole,
	queryRoles,
	queryUsersByRole,
	queryClientsByRole,
	assignUserToRole,
	unassignUserFromRole,
	assignClientToRole,
	unassignClientFromRole,
	assignGroupToRole,
	unassignGroupFromRole,
	queryGroupsByRole,
	assignMappingToRole,
	unassignMappingFromRole,
	queryMappingRulesByRole,
	roleSchema,
	createRoleRequestBodySchema,
	createRoleResponseBodySchema,
	updateRoleRequestBodySchema,
	updateRoleResponseBodySchema,
	queryRolesRequestBodySchema,
	queryRolesResponseBodySchema,
	queryUsersByRoleRequestBodySchema,
	queryUsersByRoleResponseBodySchema,
	queryClientsByRoleRequestBodySchema,
	queryClientsByRoleResponseBodySchema,
	queryGroupsByRoleRequestBodySchema,
	queryGroupsByRoleResponseBodySchema,
	queryMappingRulesByRoleRequestBodySchema,
	queryMappingRulesByRoleResponseBodySchema,
};

export type {
	Role,
	CreateRoleRequestBody,
	CreateRoleResponseBody,
	UpdateRoleRequestBody,
	UpdateRoleResponseBody,
	QueryRolesRequestBody,
	QueryRolesResponseBody,
	QueryUsersByRoleRequestBody,
	QueryUsersByRoleResponseBody,
	QueryClientsByRoleRequestBody,
	QueryClientsByRoleResponseBody,
	QueryGroupsByRoleRequestBody,
	QueryGroupsByRoleResponseBody,
	QueryMappingRulesByRoleRequestBody,
	QueryMappingRulesByRoleResponseBody,
};
