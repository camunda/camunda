/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {API_VERSION, type Endpoint} from './common';
import {getGroupStatus200Schema} from './gen/zod/getGroupSchema';
import {groupClientSearchQueryRequestSchema} from './gen/zod/groupClientSearchQueryRequestSchema';
import {groupClientSearchResultSchema} from './gen/zod/groupClientSearchResultSchema';
import {groupCreateRequestSchema} from './gen/zod/groupCreateRequestSchema';
import {groupCreateResultSchema} from './gen/zod/groupCreateResultSchema';
import {groupMappingRuleSearchResultSchema} from './gen/zod/groupMappingRuleSearchResultSchema';
import {groupRoleSearchResultSchema} from './gen/zod/groupRoleSearchResultSchema';
import {groupSearchQueryRequestSchema} from './gen/zod/groupSearchQueryRequestSchema';
import {groupSearchQueryResultSchema} from './gen/zod/groupSearchQueryResultSchema';
import {groupUpdateRequestSchema} from './gen/zod/groupUpdateRequestSchema';
import {groupUpdateResultSchema} from './gen/zod/groupUpdateResultSchema';
import {groupUserSearchQueryRequestSchema} from './gen/zod/groupUserSearchQueryRequestSchema';
import {groupUserSearchResultSchema} from './gen/zod/groupUserSearchResultSchema';
import {mappingRuleSearchQueryRequestSchema} from './gen/zod/mappingRuleSearchQueryRequestSchema';
import {roleSearchQueryRequestSchema} from './gen/zod/roleSearchQueryRequestSchema';
import type {GetGroupStatus200} from './gen/types/GetGroup';
import type {GroupClientSearchQueryRequest} from './gen/types/GroupClientSearchQueryRequest';
import type {GroupClientSearchResult} from './gen/types/GroupClientSearchResult';
import type {GroupCreateRequest} from './gen/types/GroupCreateRequest';
import type {GroupCreateResult} from './gen/types/GroupCreateResult';
import type {GroupMappingRuleSearchResult} from './gen/types/GroupMappingRuleSearchResult';
import type {GroupRoleSearchResult} from './gen/types/GroupRoleSearchResult';
import type {GroupSearchQueryRequest} from './gen/types/GroupSearchQueryRequest';
import type {GroupSearchQueryResult} from './gen/types/GroupSearchQueryResult';
import type {GroupUpdateRequest} from './gen/types/GroupUpdateRequest';
import type {GroupUpdateResult} from './gen/types/GroupUpdateResult';
import type {GroupUserSearchQueryRequest} from './gen/types/GroupUserSearchQueryRequest';
import type {GroupUserSearchResult} from './gen/types/GroupUserSearchResult';
import type {MappingRuleSearchQueryRequest} from './gen/types/MappingRuleSearchQueryRequest';
import type {RoleSearchQueryRequest} from './gen/types/RoleSearchQueryRequest';
import {groupSchema, type Group} from './group-role';
import type {MappingRule} from './mapping-rule';

const createGroupRequestBodySchema = groupCreateRequestSchema;
type CreateGroupRequestBody = GroupCreateRequest;

const createGroupResponseBodySchema = groupCreateResultSchema;
type CreateGroupResponseBody = GroupCreateResult;

const getGroupResponseBodySchema = getGroupStatus200Schema;
type GetGroupResponseBody = GetGroupStatus200;

const updateGroupRequestBodySchema = groupUpdateRequestSchema;
type UpdateGroupRequestBody = GroupUpdateRequest;

const updateGroupResponseBodySchema = groupUpdateResultSchema;
type UpdateGroupResponseBody = GroupUpdateResult;

const queryGroupsRequestBodySchema = groupSearchQueryRequestSchema;
type QueryGroupsRequestBody = GroupSearchQueryRequest;

const queryGroupsResponseBodySchema = groupSearchQueryResultSchema;
type QueryGroupsResponseBody = GroupSearchQueryResult;

const queryUsersByGroupRequestBodySchema = groupUserSearchQueryRequestSchema;
type QueryUsersByGroupRequestBody = GroupUserSearchQueryRequest;

const queryUsersByGroupResponseBodySchema = groupUserSearchResultSchema;
type QueryUsersByGroupResponseBody = GroupUserSearchResult;

const queryClientsByGroupRequestBodySchema = groupClientSearchQueryRequestSchema;
type QueryClientsByGroupRequestBody = GroupClientSearchQueryRequest;

const queryClientsByGroupResponseBodySchema = groupClientSearchResultSchema;
type QueryClientsByGroupResponseBody = GroupClientSearchResult;

const queryRolesByGroupRequestBodySchema = roleSearchQueryRequestSchema;
type QueryRolesByGroupRequestBody = RoleSearchQueryRequest;

const queryRolesByGroupResponseBodySchema = groupRoleSearchResultSchema;
type QueryRolesByGroupResponseBody = GroupRoleSearchResult;

const queryMappingRulesByGroupRequestBodySchema = mappingRuleSearchQueryRequestSchema;
type QueryMappingRulesByGroupRequestBody = MappingRuleSearchQueryRequest;

const queryMappingRulesByGroupResponseBodySchema = groupMappingRuleSearchResultSchema;
type QueryMappingRulesByGroupResponseBody = GroupMappingRuleSearchResult;

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

const queryGroups = {
	method: 'POST',
	getUrl() {
		return `/${API_VERSION}/groups/search` as const;
	},
} as const satisfies Endpoint;

const queryUsersByGroup = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}/users/search` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'>>;

const queryClientsByGroup = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}/clients/search` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'>>;

const queryRolesByGroup = {
	method: 'POST',
	getUrl(params) {
		const {groupId} = params;

		return `/${API_VERSION}/groups/${encodeURIComponent(groupId)}/roles/search` as const;
	},
} as const satisfies Endpoint<Pick<Group, 'groupId'>>;

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
