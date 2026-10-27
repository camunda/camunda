/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, getQueryRequestBodySchema, getQueryResponseBodySchema, type Endpoint} from '../common';
import {groupSchema, roleSchema, type Group, type Role} from './group-role';
import {mappingRuleSchema, type MappingRule} from './mapping-rule';
import {userSchema} from './user';

const createRoleRequestBodySchema = roleSchema;
type CreateRoleRequestBody = z.infer<typeof createRoleRequestBodySchema>;

const createRoleResponseBodySchema = roleSchema;
type CreateRoleResponseBody = z.infer<typeof createRoleResponseBodySchema>;

const updateRoleRequestBodySchema = roleSchema.pick({
	name: true,
	description: true,
});
type UpdateRoleRequestBody = z.infer<typeof updateRoleRequestBodySchema>;

const updateRoleResponseBodySchema = roleSchema;
type UpdateRoleResponseBody = z.infer<typeof updateRoleResponseBodySchema>;

const queryRolesRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['name', 'roleId'] as const,
	filter: roleSchema
		.pick({
			roleId: true,
			name: true,
		})
		.partial(),
});
type QueryRolesRequestBody = z.infer<typeof queryRolesRequestBodySchema>;

const queryRolesResponseBodySchema = getQueryResponseBodySchema(roleSchema);
type QueryRolesResponseBody = z.infer<typeof queryRolesResponseBodySchema>;

const queryUsersByRoleRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['username'] as const,
	filter: z.never(),
});
type QueryUsersByRoleRequestBody = z.infer<typeof queryUsersByRoleRequestBodySchema>;

const queryUsersByRoleResponseBodySchema = getQueryResponseBodySchema(userSchema.pick({username: true}));
type QueryUsersByRoleResponseBody = z.infer<typeof queryUsersByRoleResponseBodySchema>;

const queryClientsByRoleRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['clientId'] as const,
	filter: z.never(),
});
type QueryClientsByRoleRequestBody = z.infer<typeof queryClientsByRoleRequestBodySchema>;

const queryClientsByRoleResponseBodySchema = getQueryResponseBodySchema(
	z.object({
		clientId: z.string(),
	}),
);
type QueryClientsByRoleResponseBody = z.infer<typeof queryClientsByRoleResponseBodySchema>;

const queryGroupsByRoleRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['name', 'groupId'] as const,
	filter: groupSchema
		.pick({
			groupId: true,
			name: true,
		})
		.partial(),
});
type QueryGroupsByRoleRequestBody = z.infer<typeof queryGroupsByRoleRequestBodySchema>;

const queryGroupsByRoleResponseBodySchema = getQueryResponseBodySchema(groupSchema);
type QueryGroupsByRoleResponseBody = z.infer<typeof queryGroupsByRoleResponseBodySchema>;

const queryMappingRulesByRoleRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['claimName', 'claimValue', 'name'] as const,
	filter: mappingRuleSchema
		.pick({
			claimName: true,
			claimValue: true,
			name: true,
		})
		.partial(),
});
type QueryMappingRulesByRoleRequestBody = z.infer<typeof queryMappingRulesByRoleRequestBodySchema>;

const queryMappingRulesByRoleResponseBodySchema = getQueryResponseBodySchema(mappingRuleSchema);
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
