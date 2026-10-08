/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryKey} from '@tanstack/react-query';
import type {
	QueryClientsByRoleResponseBody,
	QueryGroupsByRoleResponseBody,
	QueryMappingRulesByRoleResponseBody,
	QueryUsersByRoleResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {queries} from '#/shared/http/queries';

const MEMBER_KINDS = ['users', 'groups', 'mappingRules', 'clients'] as const;

type MemberKind = (typeof MEMBER_KINDS)[number];

type MemberRow = {
	id: string;
	name?: string;
	email?: string;
	claimName?: string;
	claimValue?: string;
};

type MembersResponse =
	| QueryUsersByRoleResponseBody
	| QueryGroupsByRoleResponseBody
	| QueryMappingRulesByRoleResponseBody
	| QueryClientsByRoleResponseBody;

type MemberPage = {page: {from: number; limit: number}};

const MEMBER_KIND_CONFIG = {
	users: {
		query: queries.queryUsersByRole,
		assign: (roleId: string, id: string) => endpoints.assignUserToRole({roleId, username: id}),
		unassign: (roleId: string, id: string) => endpoints.unassignUserFromRole({roleId, username: id}),
		toRow: (item: QueryUsersByRoleResponseBody['items'][number]): MemberRow => ({id: item.username}),
	},
	groups: {
		query: queries.queryGroupsByRole,
		assign: (roleId: string, id: string) => endpoints.assignGroupToRole({roleId, groupId: id}),
		unassign: (roleId: string, id: string) => endpoints.unassignGroupFromRole({roleId, groupId: id}),
		toRow: (item: QueryGroupsByRoleResponseBody['items'][number]): MemberRow => ({id: item.groupId}),
	},
	mappingRules: {
		query: queries.queryMappingRulesByRole,
		assign: (roleId: string, id: string) => endpoints.assignMappingToRole({roleId, mappingRuleId: id}),
		unassign: (roleId: string, id: string) => endpoints.unassignMappingFromRole({roleId, mappingRuleId: id}),
		toRow: (item: QueryMappingRulesByRoleResponseBody['items'][number]): MemberRow => ({
			id: item.mappingRuleId,
			name: item.name,
			claimName: item.claimName,
			claimValue: item.claimValue,
		}),
	},
	clients: {
		query: queries.queryClientsByRole,
		assign: (roleId: string, id: string) => endpoints.assignClientToRole({roleId, clientId: id}),
		unassign: (roleId: string, id: string) => endpoints.unassignClientFromRole({roleId, clientId: id}),
		toRow: (item: QueryClientsByRoleResponseBody['items'][number]): MemberRow => ({id: item.clientId}),
	},
} satisfies Record<MemberKind, unknown>;

function getMembersQuery(kind: MemberKind, roleId: string, body: MemberPage) {
	const query = MEMBER_KIND_CONFIG[kind].query(roleId, body);
	return {
		queryKey: query.queryKey as QueryKey,
		queryFn: query.queryFn as () => Promise<MembersResponse>,
	};
}

function toMemberRow(kind: MemberKind, item: MembersResponse['items'][number]): MemberRow {
	// The union of per-kind mappers cannot express that `item` belongs to `kind`.
	return (MEMBER_KIND_CONFIG[kind].toRow as (item: MembersResponse['items'][number]) => MemberRow)(item);
}

function getAssignRequest(kind: MemberKind, roleId: string, id: string) {
	return MEMBER_KIND_CONFIG[kind].assign(roleId, id);
}

function getUnassignRequest(kind: MemberKind, roleId: string, id: string) {
	return MEMBER_KIND_CONFIG[kind].unassign(roleId, id);
}

function getMembersQueryKeyPrefix(roleId: string, kind: MemberKind) {
	return ['roleMembers', roleId, kind] as const;
}

export {MEMBER_KINDS, getAssignRequest, getMembersQuery, getMembersQueryKeyPrefix, getUnassignRequest, toMemberRow};
export type {MemberKind, MemberPage, MemberRow, MembersResponse};
