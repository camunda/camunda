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

const createRole: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/roles`;
	},
};

const getRole: Endpoint<Pick<Role, 'roleId'>> = {
	method: 'GET',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${roleId}`;
	},
};

const updateRole: Endpoint<Pick<Role, 'roleId'>> = {
	method: 'PUT',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${roleId}`;
	},
};

const deleteRole: Endpoint<Pick<Role, 'roleId'>> = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${roleId}`;
	},
};

const queryRoles: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/roles/search`;
	},
};

const queryUsersByRole: Endpoint<Pick<Role, 'roleId'>> = {
	method: 'POST',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${roleId}/users/search`;
	},
};

const queryClientsByRole: Endpoint<Pick<Role, 'roleId'>> = {
	method: 'POST',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${roleId}/clients/search`;
	},
};

const assignUserToRole: Endpoint<Pick<Role, 'roleId'> & {username: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {roleId, username} = params;

		return `/${API_VERSION}/roles/${roleId}/users/${username}`;
	},
};

const unassignUserFromRole: Endpoint<Pick<Role, 'roleId'> & {username: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId, username} = params;

		return `/${API_VERSION}/roles/${roleId}/users/${username}`;
	},
};

const assignClientToRole: Endpoint<Pick<Role, 'roleId'> & {clientId: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {roleId, clientId} = params;

		return `/${API_VERSION}/roles/${roleId}/clients/${clientId}`;
	},
};

const unassignClientFromRole: Endpoint<Pick<Role, 'roleId'> & {clientId: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId, clientId} = params;

		return `/${API_VERSION}/roles/${roleId}/clients/${clientId}`;
	},
};

const assignGroupToRole: Endpoint<Pick<Role, 'roleId'> & Pick<Group, 'groupId'>> = {
	method: 'PUT',
	getUrl(params) {
		const {roleId, groupId} = params;

		return `/${API_VERSION}/roles/${roleId}/groups/${groupId}`;
	},
};

const unassignGroupFromRole: Endpoint<Pick<Role, 'roleId'> & Pick<Group, 'groupId'>> = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId, groupId} = params;

		return `/${API_VERSION}/roles/${roleId}/groups/${groupId}`;
	},
};

const queryGroupsByRole: Endpoint<Pick<Role, 'roleId'>> = {
	method: 'POST',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${roleId}/groups/search`;
	},
};

const assignMappingToRole: Endpoint<Pick<Role, 'roleId'> & Pick<MappingRule, 'mappingId'>> = {
	method: 'PUT',
	getUrl(params) {
		const {roleId, mappingId} = params;

		return `/${API_VERSION}/roles/${roleId}/mappings/${mappingId}`;
	},
};

const unassignMappingFromRole: Endpoint<Pick<Role, 'roleId'> & Pick<MappingRule, 'mappingId'>> = {
	method: 'DELETE',
	getUrl(params) {
		const {roleId, mappingId} = params;

		return `/${API_VERSION}/roles/${roleId}/mappings/${mappingId}`;
	},
};

const queryMappingRulesByRole: Endpoint<Pick<Role, 'roleId'>> = {
	method: 'POST',
	getUrl(params) {
		const {roleId} = params;

		return `/${API_VERSION}/roles/${roleId}/mapping-rules/search`;
	},
};

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
