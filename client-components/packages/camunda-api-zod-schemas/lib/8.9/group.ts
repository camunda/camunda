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
	groupResultSchema,
	groupCreateRequestSchema,
	groupCreateResultSchema,
	groupUpdateRequestSchema,
	groupUpdateResultSchema,
	groupSearchQueryRequestSchema,
	groupSearchQueryResultSchema,
	groupUserSearchQueryRequestSchema,
	groupUserSearchResultSchema,
	groupClientSearchQueryRequestSchema,
	groupClientSearchResultSchema,
	roleGroupSearchQueryRequestSchema,
	roleGroupSearchResultSchema,
	mappingRuleSearchQueryRequestSchema,
	mappingRuleSearchQueryResultSchema,
} from './gen';

const groupSchema = groupResultSchema;
type Group = z.infer<typeof groupSchema>;

const createGroupRequestBodySchema = groupCreateRequestSchema;
type CreateGroupRequestBody = z.infer<typeof createGroupRequestBodySchema>;

const createGroupResponseBodySchema = groupCreateResultSchema;
type CreateGroupResponseBody = z.infer<typeof createGroupResponseBodySchema>;

const createGroup: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/groups`;
	},
};

const getGroup: Endpoint<{groupId: string}> = {
	method: 'GET',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${groupId}`;
	},
};

const getGroupResponseBodySchema = groupResultSchema;
type GetGroupResponseBody = z.infer<typeof getGroupResponseBodySchema>;

const updateGroupRequestBodySchema = groupUpdateRequestSchema;
type UpdateGroupRequestBody = z.infer<typeof updateGroupRequestBodySchema>;

const updateGroupResponseBodySchema = groupUpdateResultSchema;
type UpdateGroupResponseBody = z.infer<typeof updateGroupResponseBodySchema>;

const updateGroup: Endpoint<{groupId: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${groupId}`;
	},
};

const deleteGroup: Endpoint<{groupId: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${groupId}`;
	},
};

const queryGroupsRequestBodySchema = groupSearchQueryRequestSchema;
type QueryGroupsRequestBody = z.infer<typeof queryGroupsRequestBodySchema>;

const queryGroupsResponseBodySchema = groupSearchQueryResultSchema;
type QueryGroupsResponseBody = z.infer<typeof queryGroupsResponseBodySchema>;

const queryGroups: Endpoint = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/groups/search`;
	},
};

const queryUsersByGroupRequestBodySchema = groupUserSearchQueryRequestSchema;
type QueryUsersByGroupRequestBody = z.infer<typeof queryUsersByGroupRequestBodySchema>;

const queryUsersByGroupResponseBodySchema = groupUserSearchResultSchema;
type QueryUsersByGroupResponseBody = z.infer<typeof queryUsersByGroupResponseBodySchema>;

const queryUsersByGroup: Endpoint<{groupId: string}> = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${groupId}/users/search`;
	},
};

const queryClientsByGroupRequestBodySchema = groupClientSearchQueryRequestSchema;
type QueryClientsByGroupRequestBody = z.infer<typeof queryClientsByGroupRequestBodySchema>;

const queryClientsByGroupResponseBodySchema = groupClientSearchResultSchema;
type QueryClientsByGroupResponseBody = z.infer<typeof queryClientsByGroupResponseBodySchema>;

const queryClientsByGroup: Endpoint<{groupId: string}> = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${groupId}/clients/search`;
	},
};

const queryRolesByGroupRequestBodySchema = roleGroupSearchQueryRequestSchema;
type QueryRolesByGroupRequestBody = z.infer<typeof queryRolesByGroupRequestBodySchema>;

const queryRolesByGroupResponseBodySchema = roleGroupSearchResultSchema;
type QueryRolesByGroupResponseBody = z.infer<typeof queryRolesByGroupResponseBodySchema>;

const queryRolesByGroup: Endpoint<{groupId: string}> = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${groupId}/roles/search`;
	},
};

const queryMappingRulesByGroupRequestBodySchema = mappingRuleSearchQueryRequestSchema;
type QueryMappingRulesByGroupRequestBody = z.infer<typeof queryMappingRulesByGroupRequestBodySchema>;

const queryMappingRulesByGroupResponseBodySchema = mappingRuleSearchQueryResultSchema;
type QueryMappingRulesByGroupResponseBody = z.infer<typeof queryMappingRulesByGroupResponseBodySchema>;

const queryMappingRulesByGroup: Endpoint<{groupId: string}> = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${groupId}/mapping-rules/search`;
	},
};

const assignUserToGroup: Endpoint<{groupId: string; username: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {groupId, username} = params;

		return `/${API_VERSION}/groups/${groupId}/users/${username}`;
	},
};

const unassignUserFromGroup: Endpoint<{groupId: string; username: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {groupId, username} = params;

		return `/${API_VERSION}/groups/${groupId}/users/${username}`;
	},
};

const assignClientToGroup: Endpoint<{groupId: string; clientId: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {groupId, clientId} = params;

		return `/${API_VERSION}/groups/${groupId}/clients/${clientId}`;
	},
};

const unassignClientFromGroup: Endpoint<{groupId: string; clientId: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {groupId, clientId} = params;

		return `/${API_VERSION}/groups/${groupId}/clients/${clientId}`;
	},
};

const assignMappingToGroup: Endpoint<{groupId: string; mappingRuleId: string}> = {
	method: 'PUT',
	getUrl(params) {
		const {groupId, mappingRuleId} = params;

		return `/${API_VERSION}/groups/${groupId}/mapping-rules/${mappingRuleId}`;
	},
};

const unassignMappingFromGroup: Endpoint<{groupId: string; mappingRuleId: string}> = {
	method: 'DELETE',
	getUrl(params) {
		const {groupId, mappingRuleId} = params;

		return `/${API_VERSION}/groups/${groupId}/mapping-rules/${mappingRuleId}`;
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
