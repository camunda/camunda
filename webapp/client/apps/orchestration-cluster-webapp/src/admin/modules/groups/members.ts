/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryKey} from '@tanstack/react-query';
import type {
	QueryMappingRulesByGroupResponseBody,
	QueryRolesByGroupResponseBody,
	QueryUsersByGroupResponseBody,
	QueryClientsByGroupResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';

const MEMBER_KINDS = ['users', 'roles', 'mappingRules', 'clients'] as const;

type MemberKind = (typeof MEMBER_KINDS)[number];

type MemberRow = {
	id: string;
	name?: string;
	email?: string;
	claimName?: string;
	claimValue?: string;
};

type MembersResponse =
	| QueryUsersByGroupResponseBody
	| QueryClientsByGroupResponseBody
	| QueryRolesByGroupResponseBody
	| QueryMappingRulesByGroupResponseBody;

type MemberPage = {page: {from: number; limit: number}};

const MEMBER_KIND_CONFIG = {
	users: {
		query: queries.queryUsersByGroup,
		assign: (groupId: string, id: string) => endpoints.assignUserToGroup({groupId, username: id}),
		unassign: (groupId: string, id: string) => endpoints.unassignUserFromGroup({groupId, username: id}),
		toRow: (item: QueryUsersByGroupResponseBody['items'][number]): MemberRow => ({id: item.username}),
	},
	clients: {
		query: queries.queryClientsByGroup,
		assign: (groupId: string, id: string) => endpoints.assignClientToGroup({groupId, clientId: id}),
		unassign: (groupId: string, id: string) => endpoints.unassignClientFromGroup({groupId, clientId: id}),
		toRow: (item: QueryClientsByGroupResponseBody['items'][number]): MemberRow => ({id: item.clientId}),
	},
	roles: {
		query: queries.queryRolesByGroup,
		assign: (groupId: string, id: string) => endpoints.assignGroupToRole({roleId: id, groupId}),
		unassign: (groupId: string, id: string) => endpoints.unassignGroupFromRole({roleId: id, groupId}),
		toRow: (item: QueryRolesByGroupResponseBody['items'][number]): MemberRow => ({id: item.roleId, name: item.name}),
	},
	mappingRules: {
		query: queries.queryMappingRulesByGroup,
		assign: (groupId: string, id: string) => endpoints.assignMappingToGroup({groupId, mappingRuleId: id}),
		unassign: (groupId: string, id: string) => endpoints.unassignMappingFromGroup({groupId, mappingRuleId: id}),
		toRow: (item: QueryMappingRulesByGroupResponseBody['items'][number]): MemberRow => ({
			id: item.mappingRuleId,
			name: item.name,
			claimName: item.claimName,
			claimValue: item.claimValue,
		}),
	},
} satisfies Record<MemberKind, unknown>;

function getMembersQuery(kind: MemberKind, groupId: string, body: MemberPage) {
	const query = MEMBER_KIND_CONFIG[kind].query(groupId, body);
	return {
		queryKey: query.queryKey as QueryKey,
		queryFn: query.queryFn as () => Promise<MembersResponse>,
	};
}

function toMemberRow(kind: MemberKind, item: MembersResponse['items'][number]): MemberRow {
	// The union of per-kind mappers cannot express that `item` belongs to `kind`.
	return (MEMBER_KIND_CONFIG[kind].toRow as (item: MembersResponse['items'][number]) => MemberRow)(item);
}

function getAssignRequest(kind: MemberKind, groupId: string, id: string) {
	return MEMBER_KIND_CONFIG[kind].assign(groupId, id);
}

function getUnassignRequest(kind: MemberKind, groupId: string, id: string) {
	return MEMBER_KIND_CONFIG[kind].unassign(groupId, id);
}

function getMembersQueryKeyPrefix(groupId: string, kind: MemberKind) {
	return ['groupMembers', groupId, kind] as const;
}

export {MEMBER_KINDS, getAssignRequest, getMembersQuery, getMembersQueryKeyPrefix, getUnassignRequest, toMemberRow};
export type {MemberKind, MemberPage, MemberRow, MembersResponse};
