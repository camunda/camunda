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
	roleCreateRequestSchema,
	roleCreateResultSchema,
	roleUpdateRequestSchema,
	roleUpdateResultSchema,
	roleSearchQueryRequestSchema,
	roleSearchQueryResultSchema,
	roleUserSearchQueryRequestSchema,
	roleUserSearchResultSchema,
	roleClientSearchQueryRequestSchema,
	roleClientSearchResultSchema,
	roleGroupSearchQueryRequestSchema,
	roleGroupSearchResultSchema,
	mappingRuleSearchQueryRequestSchema,
	mappingRuleSearchQueryResultSchema,
	roleResultSchema,
} from './gen';
import type {Group} from './group';
import type {MappingRule} from './mapping-rule';

const roleSchema = roleResultSchema;
type Role = z.infer<typeof roleSchema>;

const createRoleRequestBodySchema = roleCreateRequestSchema;
type CreateRoleRequestBody = z.infer<typeof createRoleRequestBodySchema>;

const createRoleResponseBodySchema = roleCreateResultSchema;
type CreateRoleResponseBody = z.infer<typeof createRoleResponseBodySchema>;

const updateRoleRequestBodySchema = roleUpdateRequestSchema;
type UpdateRoleRequestBody = z.infer<typeof updateRoleRequestBodySchema>;

const updateRoleResponseBodySchema = roleUpdateResultSchema;
type UpdateRoleResponseBody = z.infer<typeof updateRoleResponseBodySchema>;

const queryRolesRequestBodySchema = roleSearchQueryRequestSchema;
type QueryRolesRequestBody = z.infer<typeof queryRolesRequestBodySchema>;

const queryRolesResponseBodySchema = roleSearchQueryResultSchema;
type QueryRolesResponseBody = z.infer<typeof queryRolesResponseBodySchema>;

const queryUsersByRoleRequestBodySchema = roleUserSearchQueryRequestSchema;
type QueryUsersByRoleRequestBody = z.infer<typeof queryUsersByRoleRequestBodySchema>;

const queryUsersByRoleResponseBodySchema = roleUserSearchResultSchema;
type QueryUsersByRoleResponseBody = z.infer<typeof queryUsersByRoleResponseBodySchema>;

const queryClientsByRoleRequestBodySchema = roleClientSearchQueryRequestSchema;
type QueryClientsByRoleRequestBody = z.infer<typeof queryClientsByRoleRequestBodySchema>;

const queryClientsByRoleResponseBodySchema = roleClientSearchResultSchema;
type QueryClientsByRoleResponseBody = z.infer<typeof queryClientsByRoleResponseBodySchema>;

const queryGroupsByRoleRequestBodySchema = roleGroupSearchQueryRequestSchema;
type QueryGroupsByRoleRequestBody = z.infer<typeof queryGroupsByRoleRequestBodySchema>;

const queryGroupsByRoleResponseBodySchema = roleGroupSearchResultSchema;
type QueryGroupsByRoleResponseBody = z.infer<typeof queryGroupsByRoleResponseBodySchema>;

const queryMappingRulesByRoleRequestBodySchema = mappingRuleSearchQueryRequestSchema;
type QueryMappingRulesByRoleRequestBody = z.infer<typeof queryMappingRulesByRoleRequestBodySchema>;

const queryMappingRulesByRoleResponseBodySchema = mappingRuleSearchQueryResultSchema;
type QueryMappingRulesByRoleResponseBody = z.infer<typeof queryMappingRulesByRoleResponseBodySchema>;

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

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}/mappings/${encodeURIComponent(mappingRuleId)}` as const;
	},
} as const satisfies Endpoint<Pick<Role, 'roleId'> & Pick<MappingRule, 'mappingRuleId'>>;

const unassignMappingFromRole = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId, mappingRuleId} = params;

		return `/${API_VERSION}/roles/${encodeURIComponent(roleId)}/mappings/${encodeURIComponent(mappingRuleId)}` as const;
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
