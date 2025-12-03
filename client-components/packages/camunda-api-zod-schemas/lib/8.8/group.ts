/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {API_VERSION, getQueryResponseBodySchema, getQueryRequestBodySchema, type Endpoint} from '../common';
import {mappingRuleSchema, type MappingRule} from './mapping-rule';
import {userSchema} from './user';
import {roleSchema, groupSchema, type Group} from './group-role';

const createGroupRequestBodySchema = groupSchema;
type CreateGroupRequestBody = z.infer<typeof createGroupRequestBodySchema>;

const createGroupResponseBodySchema = groupSchema;
type CreateGroupResponseBody = z.infer<typeof createGroupResponseBodySchema>;

const createGroup: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/groups`;
	},
};

const getGroup: Endpoint<Pick<Group, 'groupId'>> = {
	method: 'GET',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${groupId}`;
	},
};

const getGroupResponseBodySchema = groupSchema;
type GetGroupResponseBody = z.infer<typeof getGroupResponseBodySchema>;

const updateGroupRequestBodySchema = groupSchema.pick({
	name: true,
	description: true,
});
type UpdateGroupRequestBody = z.infer<typeof updateGroupRequestBodySchema>;

const updateGroupResponseBodySchema = groupSchema;
type UpdateGroupResponseBody = z.infer<typeof updateGroupResponseBodySchema>;

const updateGroup: Endpoint<Pick<Group, 'groupId'>> = {
	method: 'PUT',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${groupId}`;
	},
};

const deleteGroup: Endpoint<Pick<Group, 'groupId'>> = {
	method: 'DELETE',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${groupId}`;
	},
};

const queryGroupsRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['name', 'groupId'] as const,
	filter: groupSchema
		.pick({
			groupId: true,
			name: true,
		})
		.partial(),
});
type QueryGroupsRequestBody = z.infer<typeof queryGroupsRequestBodySchema>;

const queryGroupsResponseBodySchema = getQueryResponseBodySchema(groupSchema);
type QueryGroupsResponseBody = z.infer<typeof queryGroupsResponseBodySchema>;

const queryGroups: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/groups/search`;
	},
};

const queryUsersByGroupRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['username'] as const,
	filter: z.never(),
});
type QueryUsersByGroupRequestBody = z.infer<typeof queryUsersByGroupRequestBodySchema>;

const queryUsersByGroupResponseBodySchema = getQueryResponseBodySchema(userSchema.pick({username: true}));
type QueryUsersByGroupResponseBody = z.infer<typeof queryUsersByGroupResponseBodySchema>;

const queryUsersByGroup: Endpoint<Pick<Group, 'groupId'>> = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${groupId}/users/search`;
	},
};

const queryClientsByGroupRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['clientId'] as const,
	filter: z.never(),
});
type QueryClientsByGroupRequestBody = z.infer<typeof queryClientsByGroupRequestBodySchema>;

const queryClientsByGroupResponseBodySchema = getQueryResponseBodySchema(
	z.object({
		clientId: z.string(),
	}),
);
type QueryClientsByGroupResponseBody = z.infer<typeof queryClientsByGroupResponseBodySchema>;

const queryClientsByGroup: Endpoint<Pick<Group, 'groupId'>> = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${groupId}/clients/search`;
	},
};

const queryRolesByGroupRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['name', 'roleId'] as const,
	filter: z
		.object({
			roleId: z.string(),
			name: z.string(),
		})
		.partial(),
});
type QueryRolesByGroupRequestBody = z.infer<typeof queryRolesByGroupRequestBodySchema>;

const queryRolesByGroupResponseBodySchema = getQueryResponseBodySchema(roleSchema.pick({roleId: true, name: true}));
type QueryRolesByGroupResponseBody = z.infer<typeof queryRolesByGroupResponseBodySchema>;

const queryRolesByGroup: Endpoint<Pick<Group, 'groupId'>> = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${groupId}/roles/search`;
	},
};

const queryMappingRulesByGroupRequestBodySchema = getQueryRequestBodySchema({
	sortFields: ['claimName', 'claimValue', 'name'] as const,
	filter: mappingRuleSchema
		.pick({
			claimName: true,
			claimValue: true,
			name: true,
		})
		.partial(),
});
type QueryMappingRulesByGroupRequestBody = z.infer<typeof queryMappingRulesByGroupRequestBodySchema>;

const queryMappingRulesByGroupResponseBodySchema = getQueryResponseBodySchema(mappingRuleSchema);
type QueryMappingRulesByGroupResponseBody = z.infer<typeof queryMappingRulesByGroupResponseBodySchema>;

const queryMappingRulesByGroup: Endpoint<Pick<Group, 'groupId'>> = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${groupId}/mapping-rules/search`;
	},
};

const assignUserToGroup: Endpoint<Pick<Group, 'groupId'> & {username: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {groupId, username} = params;

		return `/${API_VERSION}/groups/${groupId}/users/${username}`;
	},
};

const unassignUserFromGroup: Endpoint<Pick<Group, 'groupId'> & {username: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {groupId, username} = params;

		return `/${API_VERSION}/groups/${groupId}/users/${username}`;
	},
};

const assignClientToGroup: Endpoint<Pick<Group, 'groupId'> & {clientId: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {groupId, clientId} = params;

		return `/${API_VERSION}/groups/${groupId}/clients/${clientId}`;
	},
};

const unassignClientFromGroup: Endpoint<Pick<Group, 'groupId'> & {clientId: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {groupId, clientId} = params;

		return `/${API_VERSION}/groups/${groupId}/clients/${clientId}`;
	},
};

const assignMappingToGroup: Endpoint<Pick<Group, 'groupId'> & Pick<MappingRule, 'mappingId'>> = {
	method: 'PUT',
	getUrl(params) {
		const {groupId, mappingId} = params;

		return `/${API_VERSION}/groups/${groupId}/mapping-rules/${mappingId}`;
	},
};

const unassignMappingFromGroup: Endpoint<Pick<Group, 'groupId'> & Pick<MappingRule, 'mappingId'>> = {
	method: 'DELETE',
	getUrl(params) {
		const {groupId, mappingId} = params;

		return `/${API_VERSION}/groups/${groupId}/mapping-rules/${mappingId}`;
	},
};

export {
	createGroup,
	getGroup,
	updateGroup,
	deleteGroup,
	queryGroups,
	queryUsersByGroup,
	queryClientsByGroup,
	queryRolesByGroup,
	queryMappingRulesByGroup,
	assignUserToGroup,
	unassignUserFromGroup,
	assignClientToGroup,
	unassignClientFromGroup,
	assignMappingToGroup,
	unassignMappingFromGroup,
	createGroupRequestBodySchema,
	createGroupResponseBodySchema,
	getGroupResponseBodySchema,
	updateGroupRequestBodySchema,
	updateGroupResponseBodySchema,
	queryGroupsRequestBodySchema,
	queryGroupsResponseBodySchema,
	queryUsersByGroupRequestBodySchema,
	queryUsersByGroupResponseBodySchema,
	queryClientsByGroupRequestBodySchema,
	queryClientsByGroupResponseBodySchema,
	queryRolesByGroupRequestBodySchema,
	queryRolesByGroupResponseBodySchema,
	queryMappingRulesByGroupRequestBodySchema,
	queryMappingRulesByGroupResponseBodySchema,
	groupSchema,
};
export type {
	Group,
	CreateGroupRequestBody,
	CreateGroupResponseBody,
	GetGroupResponseBody,
	UpdateGroupRequestBody,
	UpdateGroupResponseBody,
	QueryGroupsRequestBody,
	QueryGroupsResponseBody,
	QueryUsersByGroupRequestBody,
	QueryUsersByGroupResponseBody,
	QueryClientsByGroupRequestBody,
	QueryClientsByGroupResponseBody,
	QueryRolesByGroupRequestBody,
	QueryRolesByGroupResponseBody,
	QueryMappingRulesByGroupRequestBody,
	QueryMappingRulesByGroupResponseBody,
};
