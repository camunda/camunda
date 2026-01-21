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
import type {MappingRule} from './mapping-rule';

const groupSchema = groupResultSchema;
type Group = z.infer<typeof groupSchema>;

const createGroupRequestBodySchema = groupCreateRequestSchema;
type CreateGroupRequestBody = z.infer<typeof createGroupRequestBodySchema>;

const createGroupResponseBodySchema = groupCreateResultSchema;
type CreateGroupResponseBody = z.infer<typeof createGroupResponseBodySchema>;

const createGroup = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/groups` as const;
	},
} as const satisfies Endpoint;

const getGroup = {
	method: 'GET',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'>>;

const getGroupResponseBodySchema = groupResultSchema;
type GetGroupResponseBody = z.infer<typeof getGroupResponseBodySchema>;

const updateGroupRequestBodySchema = groupUpdateRequestSchema;
type UpdateGroupRequestBody = z.infer<typeof updateGroupRequestBodySchema>;

const updateGroupResponseBodySchema = groupUpdateResultSchema;
type UpdateGroupResponseBody = z.infer<typeof updateGroupResponseBodySchema>;

const updateGroup = {
	method: 'PUT',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'>>;

const deleteGroup = {
	method: 'DELETE',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'>>;

const queryGroupsRequestBodySchema = groupSearchQueryRequestSchema;
type QueryGroupsRequestBody = z.infer<typeof queryGroupsRequestBodySchema>;

const queryGroupsResponseBodySchema = groupSearchQueryResultSchema;
type QueryGroupsResponseBody = z.infer<typeof queryGroupsResponseBodySchema>;

const queryGroups = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/groups/search` as const;
	},
} as const satisfies Endpoint;

const queryUsersByGroupRequestBodySchema = groupUserSearchQueryRequestSchema;
type QueryUsersByGroupRequestBody = z.infer<typeof queryUsersByGroupRequestBodySchema>;

const queryUsersByGroupResponseBodySchema = groupUserSearchResultSchema;
type QueryUsersByGroupResponseBody = z.infer<typeof queryUsersByGroupResponseBodySchema>;

const queryUsersByGroup = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}/users/search` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'>>;

const queryClientsByGroupRequestBodySchema = groupClientSearchQueryRequestSchema;
type QueryClientsByGroupRequestBody = z.infer<typeof queryClientsByGroupRequestBodySchema>;

const queryClientsByGroupResponseBodySchema = groupClientSearchResultSchema;
type QueryClientsByGroupResponseBody = z.infer<typeof queryClientsByGroupResponseBodySchema>;

const queryClientsByGroup = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}/clients/search` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'>>;

const queryRolesByGroupRequestBodySchema = roleGroupSearchQueryRequestSchema;
type QueryRolesByGroupRequestBody = z.infer<typeof queryRolesByGroupRequestBodySchema>;

const queryRolesByGroupResponseBodySchema = roleGroupSearchResultSchema;
type QueryRolesByGroupResponseBody = z.infer<typeof queryRolesByGroupResponseBodySchema>;

const queryRolesByGroup = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}/roles/search` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'>>;

const queryMappingRulesByGroupRequestBodySchema = mappingRuleSearchQueryRequestSchema;
type QueryMappingRulesByGroupRequestBody = z.infer<typeof queryMappingRulesByGroupRequestBodySchema>;

const queryMappingRulesByGroupResponseBodySchema = mappingRuleSearchQueryResultSchema;
type QueryMappingRulesByGroupResponseBody = z.infer<typeof queryMappingRulesByGroupResponseBodySchema>;

const queryMappingRulesByGroup = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}/mapping-rules/search` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'>>;

const assignUserToGroup = {
	method: 'PUT',
	getUrl(params) {
		const {groupId, username} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}/users/${encodeURIComponent(username)}` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'> & {username: string}>;

const unassignUserFromGroup = {
	method: 'DELETE',
	getUrl(params) {
		const {groupId, username} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}/users/${encodeURIComponent(username)}` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'> & {username: string}>;

const assignClientToGroup = {
	method: 'PUT',
	getUrl(params) {
		const {groupId, clientId} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}/clients/${encodeURIComponent(clientId)}` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'> & {clientId: string}>;

const unassignClientFromGroup = {
	method: 'DELETE',
	getUrl(params) {
		const {groupId, clientId} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}/clients/${encodeURIComponent(clientId)}` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'> & {clientId: string}>;

const assignMappingToGroup = {
	method: 'PUT',
	getUrl(params) {
		const {groupId, mappingRuleId} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}/mapping-rules/${encodeURIComponent(mappingRuleId)}` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'> & Pick<MappingRule, 'mappingRuleId'>>;

const unassignMappingFromGroup = {
	method: 'DELETE',
	getUrl(params) {
		const {groupId, mappingRuleId} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}/mapping-rules/${encodeURIComponent(mappingRuleId)}` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'> & Pick<MappingRule, 'mappingRuleId'>>;

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
